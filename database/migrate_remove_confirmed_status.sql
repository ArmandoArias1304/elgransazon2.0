-- Migration: Remove CONFIRMED status from reservations
-- Change all CONFIRMED reservations to RESERVED status
-- Author: System
-- Date: 2025-10-18

-- First, check how many reservations have CONFIRMED status
SELECT COUNT(*) as confirmed_count 
FROM reservations 
WHERE status = 'CONFIRMED';

-- Update all CONFIRMED reservations to RESERVED
-- Rationale: CONFIRMED was redundant, reservations should stay RESERVED until check-in
UPDATE reservations 
SET status = 'RESERVED' 
WHERE status = 'CONFIRMED';

-- Verify the update
SELECT COUNT(*) as confirmed_count_after 
FROM reservations 
WHERE status = 'CONFIRMED';

-- Show the current status distribution
SELECT 
    status, 
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM reservations), 2) as percentage
FROM reservations
GROUP BY status
ORDER BY count DESC;
