-- Migration: Add item-level status tracking to order_details
-- Date: 2025-11-09
-- Purpose: Allow individual item status tracking for orders
-- This enables:
-- 1. Adding new items to existing orders
-- 2. Tracking which items are new vs original
-- 3. Individual item status (PENDING, IN_PREPARATION, READY, DELIVERED)
-- 4. Tracking which chef prepared each item

-- Add new columns to order_details table
ALTER TABLE order_details
ADD COLUMN item_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER comments,
ADD COLUMN is_new_item BOOLEAN NOT NULL DEFAULT FALSE AFTER item_status,
ADD COLUMN added_at DATETIME DEFAULT NULL AFTER is_new_item,
ADD COLUMN prepared_by VARCHAR(100) DEFAULT NULL AFTER added_at;

-- Add index for faster queries filtering by item status
CREATE INDEX idx_order_details_item_status ON order_details(item_status);

-- Add index for finding new items
CREATE INDEX idx_order_details_is_new_item ON order_details(is_new_item);

-- Add comment to describe the purpose
ALTER TABLE order_details COMMENT = 'Order details with individual item status tracking for flexible order management';

-- Update existing records to have proper initial values
-- All existing items should have item_status = order's current status
-- and is_new_item = FALSE (they are original items)
UPDATE order_details od
JOIN orders o ON od.id_order = o.id_order
SET 
    od.item_status = o.status,
    od.is_new_item = FALSE,
    od.added_at = od.created_at
WHERE od.item_status = 'PENDING' OR od.item_status IS NULL;

-- Verification queries (run these to check migration success)
-- SELECT * FROM order_details LIMIT 10;
-- SELECT DISTINCT item_status FROM order_details;
-- SELECT COUNT(*) as new_items FROM order_details WHERE is_new_item = TRUE;
