-- Add new columns to orders table for better tracking
-- id_prepared_by: Employee who prepared/cooked the order (Chef)
-- id_paid_by: Employee who collected payment (Cashier or Waiter)

-- Add prepared_by column
ALTER TABLE orders
ADD COLUMN id_prepared_by BIGINT NULL
AFTER id_employee;

-- Add paid_by column
ALTER TABLE orders
ADD COLUMN id_paid_by BIGINT NULL
AFTER id_prepared_by;

-- Add foreign key constraints
ALTER TABLE orders
ADD CONSTRAINT fk_orders_prepared_by
FOREIGN KEY (id_prepared_by) REFERENCES employee(id_empleado)
ON DELETE SET NULL;

ALTER TABLE orders
ADD CONSTRAINT fk_orders_paid_by
FOREIGN KEY (id_paid_by) REFERENCES employee(id_empleado)
ON DELETE SET NULL;

-- Add indexes for better query performance
CREATE INDEX idx_orders_prepared_by ON orders(id_prepared_by);
CREATE INDEX idx_orders_paid_by ON orders(id_paid_by);

-- Add comments for documentation
ALTER TABLE orders
MODIFY COLUMN id_employee BIGINT NOT NULL
COMMENT 'Employee who created/took the order (Waiter)';

ALTER TABLE orders
MODIFY COLUMN id_prepared_by BIGINT NULL
COMMENT 'Employee who prepared the order (Chef)';

ALTER TABLE orders
MODIFY COLUMN id_paid_by BIGINT NULL
COMMENT 'Employee who collected payment (Cashier or Waiter)';
