-- Migration script to add new fields to employee table
-- Date: 2025-10-16
-- Description: Add phone, salary, supervisor, last_access, audit fields

USE bd_restaurant;

-- Add new columns if they don't exist
ALTER TABLE employee
ADD COLUMN IF NOT EXISTS telefono VARCHAR(20) AFTER email,
ADD COLUMN IF NOT EXISTS salario DECIMAL(10,2) AFTER telefono,
ADD COLUMN IF NOT EXISTS ultimo_acceso DATETIME AFTER salario,
ADD COLUMN IF NOT EXISTS id_supervisor BIGINT AFTER ultimo_acceso,
ADD COLUMN IF NOT EXISTS created_by VARCHAR(50) AFTER enabled,
ADD COLUMN IF NOT EXISTS updated_by VARCHAR(50) AFTER created_by,
ADD COLUMN IF NOT EXISTS created_at DATETIME DEFAULT CURRENT_TIMESTAMP AFTER updated_by,
ADD COLUMN IF NOT EXISTS updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- Add foreign key constraint for supervisor
ALTER TABLE employee
ADD CONSTRAINT fk_employee_supervisor 
FOREIGN KEY (id_supervisor) REFERENCES employee(id_empleado) ON DELETE SET NULL;

-- Add index for better performance
CREATE INDEX idx_employee_supervisor ON employee(id_supervisor);
CREATE INDEX idx_employee_enabled ON employee(enabled);
CREATE INDEX idx_employee_last_access ON employee(ultimo_acceso);

-- Insert MANAGER and CASHIER roles if they don't exist
INSERT IGNORE INTO roles (nombre_rol) VALUES ('ROLE_MANAGER');
INSERT IGNORE INTO roles (nombre_rol) VALUES ('ROLE_CASHIER');

-- Update existing employees to set admin as their supervisor (optional)
-- This is commented out because you may want to do this manually
-- UPDATE employee e1 
-- SET id_supervisor = (SELECT id_empleado FROM employee WHERE username = 'admin' LIMIT 1)
-- WHERE e1.username != 'admin' AND e1.id_supervisor IS NULL;

COMMIT;
