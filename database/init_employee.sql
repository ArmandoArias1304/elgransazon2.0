-- ============================================
-- Database: bd_restaurant
-- Table: Employee
-- Description: Employee table for POS authentication
-- ============================================

-- Create the database if it doesn't exist
CREATE DATABASE IF NOT EXISTS bd_restaurant;
USE bd_restaurant;

-- Create Employee table
CREATE TABLE IF NOT EXISTS employee (
    id_empleado BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    contrasenia VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uk_employee_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert sample employees
-- Note: Passwords are BCrypt hashed version of 'password123'
-- You can generate BCrypt hashes at: https://bcrypt-generator.com/
-- or use the PasswordEncoder in your application

INSERT INTO employee (nombre, apellido, contrasenia, enabled) VALUES
('admin', 'Administrator', '$2a$10$xHYH5U0AqKmYJnEqJbxh0e7n3/bL9/6KJVcR1gkHqrM8KVNGQqG9u', TRUE),
('juan', 'Perez', '$2a$10$xHYH5U0AqKmYJnEqJbxh0e7n3/bL9/6KJVcR1gkHqrM8KVNGQqG9u', TRUE),
('maria', 'Garcia', '$2a$10$xHYH5U0AqKmYJnEqJbxh0e7n3/bL9/6KJVcR1gkHqrM8KVNGQqG9u', TRUE),
('carlos', 'Rodriguez', '$2a$10$xHYH5U0AqKmYJnEqJbxh0e7n3/bL9/6KJVcR1gkHqrM8KVNGQqG9u', TRUE)
ON DUPLICATE KEY UPDATE 
    apellido = VALUES(apellido),
    contrasenia = VALUES(contrasenia),
    enabled = VALUES(enabled);

-- Display created employees
SELECT * FROM employee;
