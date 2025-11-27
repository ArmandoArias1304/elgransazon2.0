-- Fix restaurant_table
USE bd_restaurant;
DROP TABLE IF EXISTS restaurant_table;

-- Recreate table correctly
CREATE TABLE restaurant_table (
    id_table BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
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

-- Indexes for performance
CREATE INDEX idx_status ON restaurant_table(status);
CREATE INDEX idx_location ON restaurant_table(location);
CREATE INDEX idx_capacity ON restaurant_table(capacity);

-- Insert sample data
INSERT INTO restaurant_table (table_number, capacity, location, status, comments, created_by) VALUES
(1, 4, 'Terraza', 'AVAILABLE', 'Mesa junto a la ventana con vista al jardín', 'admin'),
(2, 2, 'Salón Principal', 'AVAILABLE', NULL, 'admin'),
(3, 6, 'Salón Principal', 'OCCUPIED', 'Familia grande, necesitan silla para bebé', 'admin'),
(4, 4, 'Bar', 'RESERVED', 'Reserva para las 20:00', 'admin'),
(5, 8, 'Salón VIP', 'AVAILABLE', 'Mesa principal del salón VIP', 'admin'),
(6, 2, 'Bar', 'OCCUPIED', NULL, 'admin'),
(7, 4, 'Terraza', 'RESERVED', 'Aniversario - decoración especial', 'admin'),
(8, 6, 'Salón Principal', 'AVAILABLE', NULL, 'admin'),
(9, 4, 'Salón VIP', 'OUT_OF_SERVICE', 'Mantenimiento - silla rota', 'admin'),
(10, 2, 'Terraza', 'OUT_OF_SERVICE', 'Limpieza profunda', 'admin');

-- Verification
SELECT * FROM restaurant_table ORDER BY table_number;

-- Statistics
SELECT 
    status,
    COUNT(*) as total,
    SUM(capacity) as total_capacity
FROM restaurant_table 
GROUP BY status;
