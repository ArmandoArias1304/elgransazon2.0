-- Add tip column to orders table
-- Migration script to add tip (propina) support

ALTER TABLE orders
ADD COLUMN tip DECIMAL(10,2) DEFAULT 0.00 COMMENT 'Propina dejada en la orden';

-- Update existing orders to have 0 tip
UPDATE orders SET tip = 0.00 WHERE tip IS NULL;
