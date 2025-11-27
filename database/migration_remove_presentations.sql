-- ============================================================================
-- MIGRATION: Remove Presentations Table and Related Foreign Keys
-- Date: 2025-01-22
-- Description: Removes the presentations table and id_presentation column 
--              from item_menu table, as presentations are no longer needed.
--              Each ItemMenu now directly relates to ingredients only.
-- ============================================================================

USE bd_restaurant;

-- Step 1: Drop foreign key constraint in item_menu table
ALTER TABLE item_menu 
DROP FOREIGN KEY IF EXISTS fk_item_menu_presentation;

-- Step 2: Drop the id_presentation column from item_menu
ALTER TABLE item_menu 
DROP COLUMN IF EXISTS id_presentation;

-- Step 3: Drop the presentations table
DROP TABLE IF EXISTS presentations;

-- ============================================================================
-- VERIFICATION QUERIES (Run these to verify the changes)
-- ============================================================================

-- Check that presentations table no longer exists
-- SHOW TABLES LIKE 'presentations';

-- Check that item_menu no longer has id_presentation column
-- DESCRIBE item_menu;

-- Verify foreign keys of item_menu
-- SELECT 
--     CONSTRAINT_NAME,
--     TABLE_NAME,
--     COLUMN_NAME,
--     REFERENCED_TABLE_NAME,
--     REFERENCED_COLUMN_NAME
-- FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
-- WHERE TABLE_NAME = 'item_menu'
--   AND TABLE_SCHEMA = 'bd_restaurant'
--   AND REFERENCED_TABLE_NAME IS NOT NULL;

-- ============================================================================
-- ROLLBACK SCRIPT (In case you need to revert - USE WITH CAUTION!)
-- ============================================================================

-- IMPORTANT: This rollback will recreate empty tables. 
-- You'll need to restore data from backup if needed.

/*
-- Recreate presentations table
CREATE TABLE presentations (
    id_presentation BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    id_category BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT NULL,
    UNIQUE KEY uk_presentation_name_category (name, id_category),
    CONSTRAINT fk_presentation_category 
        FOREIGN KEY (id_category) REFERENCES categories(id_category)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add id_presentation column back to item_menu
ALTER TABLE item_menu 
ADD COLUMN id_presentation BIGINT AFTER id_category;

-- Add foreign key constraint
ALTER TABLE item_menu 
ADD CONSTRAINT fk_item_menu_presentation 
    FOREIGN KEY (id_presentation) REFERENCES presentations(id_presentation)
    ON DELETE RESTRICT ON UPDATE CASCADE;

-- NOTE: You'll need to manually populate the presentations table
-- and update item_menu.id_presentation values from your backup!
*/

-- ============================================================================
-- END OF MIGRATION SCRIPT
-- ============================================================================
