-- ============================================
-- Migration Script: Add username and email fields to employee table
-- Description: Updates existing employee table to add username and email columns
-- ============================================

USE bd_restaurant;

-- Add username column if it doesn't exist
SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'bd_restaurant'
    AND TABLE_NAME = 'employee'
    AND COLUMN_NAME = 'username'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE employee ADD COLUMN username VARCHAR(50) AFTER id_empleado',
    'SELECT "Column username already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add email column if it doesn't exist
SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'bd_restaurant'
    AND TABLE_NAME = 'employee'
    AND COLUMN_NAME = 'email'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE employee ADD COLUMN email VARCHAR(100) AFTER apellido',
    'SELECT "Column email already exists" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Update existing employees with username values based on their nombre
-- This is a one-time migration, adjust usernames as needed
UPDATE employee SET username = CONCAT(LOWER(nombre), '_user') WHERE username IS NULL;
UPDATE employee SET email = CONCAT(LOWER(nombre), '@restaurant.com') WHERE email IS NULL;

-- Now add NOT NULL and UNIQUE constraints
ALTER TABLE employee 
    MODIFY COLUMN username VARCHAR(50) NOT NULL,
    MODIFY COLUMN email VARCHAR(100) NOT NULL,
    ADD UNIQUE KEY uk_employee_username (username) IF NOT EXISTS,
    ADD UNIQUE KEY uk_employee_email (email) IF NOT EXISTS;

-- Remove old unique constraint on nombre if it exists
SET @constraint_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = 'bd_restaurant'
    AND TABLE_NAME = 'employee'
    AND CONSTRAINT_NAME = 'uk_employee_nombre'
);

SET @sql = IF(@constraint_exists > 0,
    'ALTER TABLE employee DROP INDEX uk_employee_nombre',
    'SELECT "Constraint uk_employee_nombre does not exist" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Display updated table structure
DESCRIBE employee;

-- Display updated employee data
SELECT id_empleado, username, nombre, apellido, email, enabled FROM employee;

SELECT 'Migration completed successfully!' AS '';
