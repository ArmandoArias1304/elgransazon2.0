-- ============================================
-- Script: init_tables.sql
-- Description: Create and initialize restaurant tables
-- Database: bd_restaurant
-- ============================================

USE bd_restaurant;

-- Drop table if exists (for development only)
-- DROP TABLE IF EXISTS restaurant_table;

-- Create restaurant_table table
CREATE TABLE IF NOT EXISTS restaurant_table (
    id_table BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_number INT NOT NULL UNIQUE,
    capacity INT NOT NULL,
    location VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    comments TEXT,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_table_number CHECK (table_number > 0),
    CONSTRAINT chk_capacity CHECK (capacity > 0 AND capacity <= 50),
    CONSTRAINT chk_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'OUT_OF_SERVICE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create indexes for better performance
CREATE INDEX idx_restaurant_table_status ON restaurant_table(status);
CREATE INDEX idx_restaurant_table_location ON restaurant_table(location);
CREATE INDEX idx_restaurant_table_capacity ON restaurant_table(capacity);

-- Insert sample data
INSERT INTO restaurant_table (table_number, capacity, location, status, comments, created_by) VALUES
(1, 4, 'Terraza', 'AVAILABLE', 'Mesa con vista al jardín', 'admin'),
(2, 2, 'Salón Principal', 'AVAILABLE', 'Mesa pequeña cerca de la entrada', 'admin'),
(3, 6, 'Terraza', 'OCCUPIED', 'Mesa grande para familias', 'admin'),
(4, 8, 'Salón VIP', 'RESERVED', 'Mesa premium con servicio especial', 'admin'),
(5, 4, 'Salón Principal', 'AVAILABLE', NULL, 'admin'),
(6, 2, NULL, 'OUT_OF_SERVICE', 'Necesita reparación en silla', 'admin'),
(7, 4, 'Bar', 'AVAILABLE', 'Mesa alta cerca de la barra', 'admin'),
(8, 6, 'Salón Principal', 'RESERVED', 'Reservación para evento privado', 'admin'),
(9, 4, 'Terraza', 'AVAILABLE', 'Mesa con sombrilla', 'admin'),
(10, 2, 'Bar', 'OCCUPIED', NULL, 'admin');

-- Verify data
SELECT 
    table_number AS 'Número',
    capacity AS 'Capacidad',
    location AS 'Ubicación',
    status AS 'Estado',
    comments AS 'Comentarios',
    created_at AS 'Creado'
FROM restaurant_table
ORDER BY table_number;

-- Statistics
SELECT 
    status AS 'Estado',
    COUNT(*) AS 'Cantidad',
    SUM(capacity) AS 'Capacidad Total'
FROM restaurant_table
GROUP BY status
ORDER BY COUNT(*) DESC;

-- Success message
SELECT 'Tables initialized successfully!' AS 'Status';
