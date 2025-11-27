-- Migration: Make customer fields optional in orders table
-- Reason: DINE_IN orders don't require customer information
-- Date: 2025-10-23

USE bd_restaurant;

-- Modify customer_name to allow NULL
ALTER TABLE orders 
MODIFY COLUMN customer_name VARCHAR(100) NULL;

-- Modify customer_phone to allow NULL
ALTER TABLE orders 
MODIFY COLUMN customer_phone VARCHAR(20) NULL;

-- Modify delivery_address to allow NULL
ALTER TABLE orders 
MODIFY COLUMN delivery_address VARCHAR(500) NULL;

-- Display confirmation
SELECT 'Migration completed: customer fields now optional in orders table' AS Status;
