-- ============================================
-- Migration: Remove abbreviation and description from presentations
-- Date: 2025-10-22
-- Description: Simplify presentations table by removing unnecessary fields
-- ============================================

USE bd_restaurant;

-- Remove abbreviation and description columns
ALTER TABLE presentations 
    DROP COLUMN abbreviation,
    DROP COLUMN description;

-- Verify changes
DESCRIBE presentations;

-- Expected columns after migration:
-- id_presentation, name, active, id_category, created_at, updated_at
