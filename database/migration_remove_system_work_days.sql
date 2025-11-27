-- =====================================================
-- MIGRATION SCRIPT: Remove system_work_days table
-- Implements Option A: Single source of truth with BusinessHours
-- =====================================================

-- IMPORTANT: Backup your database before running this script!

-- =====================================================
-- STEP 1: Verify current data
-- =====================================================

SELECT 'Before Migration - System Configuration' as info;
SELECT * FROM system_configuration;

SELECT 'Before Migration - Work Days' as info;
SELECT * FROM system_work_days ORDER BY system_configuration_id, day_of_week;

SELECT 'Before Migration - Business Hours' as info;
SELECT * FROM business_hours ORDER BY system_configuration_id, day_of_week;

-- =====================================================
-- STEP 2: Ensure ALL days have BusinessHours records
-- This creates missing BusinessHours for any day not already present
-- =====================================================

-- Insert missing business hours for each configuration
-- Mark days as closed if they're not in system_work_days
INSERT INTO business_hours (system_configuration_id, day_of_week, open_time, close_time, is_closed)
SELECT 
    sc.id as system_configuration_id,
    days.day_of_week,
    '08:00:00' as open_time,
    '22:00:00' as close_time,
    CASE 
        WHEN swd.day_of_week IS NULL THEN TRUE  -- Not in work_days = closed
        ELSE FALSE                               -- In work_days = open
    END as is_closed
FROM system_configuration sc
CROSS JOIN (
    SELECT 'MONDAY' as day_of_week UNION ALL
    SELECT 'TUESDAY' UNION ALL
    SELECT 'WEDNESDAY' UNION ALL
    SELECT 'THURSDAY' UNION ALL
    SELECT 'FRIDAY' UNION ALL
    SELECT 'SATURDAY' UNION ALL
    SELECT 'SUNDAY'
) days
LEFT JOIN system_work_days swd 
    ON swd.system_configuration_id = sc.id 
    AND swd.day_of_week = days.day_of_week
WHERE NOT EXISTS (
    SELECT 1 
    FROM business_hours bh 
    WHERE bh.system_configuration_id = sc.id 
    AND bh.day_of_week = days.day_of_week
);

-- =====================================================
-- STEP 3: Verify business hours now cover all days
-- =====================================================

SELECT 'After Sync - Business Hours Count' as info;
SELECT 
    system_configuration_id,
    COUNT(*) as total_days,
    SUM(CASE WHEN is_closed = FALSE THEN 1 ELSE 0 END) as open_days,
    SUM(CASE WHEN is_closed = TRUE THEN 1 ELSE 0 END) as closed_days
FROM business_hours
GROUP BY system_configuration_id;

SELECT 'After Sync - All Business Hours' as info;
SELECT * FROM business_hours ORDER BY system_configuration_id, 
    CASE day_of_week
        WHEN 'MONDAY' THEN 1
        WHEN 'TUESDAY' THEN 2
        WHEN 'WEDNESDAY' THEN 3
        WHEN 'THURSDAY' THEN 4
        WHEN 'FRIDAY' THEN 5
        WHEN 'SATURDAY' THEN 6
        WHEN 'SUNDAY' THEN 7
    END;

-- =====================================================
-- STEP 4: Drop the system_work_days table
-- Once verified that all days are in business_hours
-- =====================================================

-- Uncomment the line below ONLY after verifying the data is correct
-- DROP TABLE IF EXISTS system_work_days;

-- =====================================================
-- VERIFICATION: Compare old vs new
-- =====================================================

SELECT 'Work Days (from old system_work_days)' as info;
-- This will fail after dropping the table, which is expected
-- SELECT system_configuration_id, day_of_week FROM system_work_days ORDER BY system_configuration_id, day_of_week;

SELECT 'Work Days (from new business_hours with is_closed = FALSE)' as info;
SELECT system_configuration_id, day_of_week 
FROM business_hours 
WHERE is_closed = FALSE 
ORDER BY system_configuration_id,
    CASE day_of_week
        WHEN 'MONDAY' THEN 1
        WHEN 'TUESDAY' THEN 2
        WHEN 'WEDNESDAY' THEN 3
        WHEN 'THURSDAY' THEN 4
        WHEN 'FRIDAY' THEN 5
        WHEN 'SATURDAY' THEN 6
        WHEN 'SUNDAY' THEN 7
    END;

-- =====================================================
-- ROLLBACK PLAN (if something goes wrong)
-- =====================================================

-- If you need to rollback, you can recreate system_work_days:
/*
CREATE TABLE system_work_days (
    system_configuration_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    PRIMARY KEY (system_configuration_id, day_of_week),
    FOREIGN KEY (system_configuration_id) REFERENCES system_configuration(id)
);

-- Repopulate from business_hours
INSERT INTO system_work_days (system_configuration_id, day_of_week)
SELECT system_configuration_id, day_of_week
FROM business_hours
WHERE is_closed = FALSE;
*/

-- =====================================================
-- NOTES:
-- =====================================================
-- 
-- After this migration:
-- 
-- 1. Work days are determined by: business_hours.is_closed = FALSE
-- 2. Closed days are: business_hours.is_closed = TRUE
-- 3. All 7 days of the week MUST have a business_hours record
-- 4. The SystemConfiguration.workDays field has been removed from Java code
-- 5. The SystemConfiguration.isWorkDay(day) method now queries business_hours
-- 6. The SystemConfiguration.getSortedWorkDays() method returns days where is_closed = FALSE
-- 
-- Benefits:
-- - Single source of truth (no more duplication)
-- - No more sync issues between work_days and business_hours
-- - Simpler data model
-- - Easier to maintain
-- 
-- =====================================================
