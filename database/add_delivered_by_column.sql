-- Add delivered_by column to orders table
-- This column tracks which delivery person delivered the order (only for DELIVERY orders)

ALTER TABLE orders
ADD COLUMN id_delivered_by BIGINT NULL,
ADD CONSTRAINT fk_orders_delivered_by
    FOREIGN KEY (id_delivered_by)
    REFERENCES employees(id_employee)
    ON DELETE SET NULL;

-- Add index for better performance
CREATE INDEX idx_orders_delivered_by ON orders(id_delivered_by);

-- Add comment
COMMENT ON COLUMN orders.id_delivered_by IS 'Employee who delivered the order (delivery person - only for DELIVERY orders)';
