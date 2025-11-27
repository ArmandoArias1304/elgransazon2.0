-- Migration script for Reservations System
-- Adds reservations table and modifies existing tables

-- 1. Add average_consumption_time_minutes to system_configuration
ALTER TABLE system_configuration
ADD COLUMN IF NOT EXISTS average_consumption_time_minutes INT NOT NULL DEFAULT 120;

-- Add constraint for average_consumption_time_minutes
ALTER TABLE system_configuration
ADD CONSTRAINT chk_avg_consumption_time CHECK (average_consumption_time_minutes >= 30 AND average_consumption_time_minutes <= 480);

-- 2. Add is_occupied column to restaurant_table
ALTER TABLE restaurant_table
ADD COLUMN IF NOT EXISTS is_occupied BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Create reservations table
CREATE TABLE IF NOT EXISTS reservations (
    id_reservation BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(20) NOT NULL,
    customer_email VARCHAR(100),
    number_of_guests INT NOT NULL,
    reservation_date DATE NOT NULL,
    reservation_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    special_requests VARCHAR(500),
    is_occupied BOOLEAN NOT NULL DEFAULT FALSE,
    id_table BIGINT NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_reservation_table FOREIGN KEY (id_table) REFERENCES restaurant_table(id_table) ON DELETE RESTRICT ON UPDATE CASCADE,
    
    -- Check constraints
    CONSTRAINT chk_reservation_guests CHECK (number_of_guests >= 1 AND number_of_guests <= 50),
    CONSTRAINT chk_reservation_status CHECK (status IN ('RESERVED', 'CONFIRMED', 'OCCUPIED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_reservation_date ON reservations(reservation_date);
CREATE INDEX IF NOT EXISTS idx_reservation_status ON reservations(status);
CREATE INDEX IF NOT EXISTS idx_table_date ON reservations(id_table, reservation_date);
CREATE INDEX IF NOT EXISTS idx_customer_phone ON reservations(customer_phone);
CREATE INDEX IF NOT EXISTS idx_customer_name ON reservations(customer_name);

-- 5. Insert sample data (optional - for testing)
-- Uncomment if you want to insert test data

/*
-- Get the first table ID for sample reservations
SET @first_table_id = (SELECT id_table FROM restaurant_table ORDER BY id_table LIMIT 1);

-- Insert sample reservations for today
INSERT INTO reservations (customer_name, customer_phone, customer_email, number_of_guests, 
                         reservation_date, reservation_time, status, special_requests, id_table, created_by)
VALUES 
    ('Juan Pérez', '555-1234', 'juan@email.com', 4, CURDATE(), '12:00:00', 'RESERVED', 'Mesa cerca de la ventana', @first_table_id, 'admin'),
    ('María García', '555-5678', 'maria@email.com', 2, CURDATE(), '14:30:00', 'CONFIRMED', NULL, @first_table_id + 1, 'admin'),
    ('Carlos Rodríguez', '555-9012', NULL, 6, CURDATE() + INTERVAL 1 DAY, '19:00:00', 'RESERVED', 'Cumpleaños', @first_table_id + 2, 'admin');

-- Update table statuses based on reservations
UPDATE restaurant_table rt
SET status = 'RESERVED'
WHERE id_table IN (
    SELECT DISTINCT id_table 
    FROM reservations 
    WHERE status IN ('RESERVED', 'CONFIRMED')
    AND reservation_date >= CURDATE()
);
*/

-- 6. Show created/modified structures
DESCRIBE system_configuration;
DESCRIBE restaurant_table;
DESCRIBE reservations;

-- 7. Show statistics
SELECT 'Tables with is_occupied column' AS Info, COUNT(*) AS Count FROM restaurant_table;
SELECT 'System Configuration records' AS Info, COUNT(*) AS Count FROM system_configuration;
SELECT 'Reservations created' AS Info, COUNT(*) AS Count FROM reservations;

-- Migration completed successfully
SELECT 'Migration completed successfully!' AS Status;
