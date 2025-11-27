-- Script de migración para el módulo de Configuración del Sistema
-- Fecha: 2025-10-15

-- Tabla principal de configuración del sistema (Singleton)
CREATE TABLE IF NOT EXISTS system_configuration (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    restaurant_name VARCHAR(100) NOT NULL,
    slogan VARCHAR(255),
    logo_url VARCHAR(500),
    address VARCHAR(500) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100) NOT NULL,
    tax_rate DECIMAL(5, 2) NOT NULL DEFAULT 16.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla para días laborales del sistema
CREATE TABLE IF NOT EXISTS system_work_days (
    system_configuration_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    PRIMARY KEY (system_configuration_id, day_of_week),
    CONSTRAINT fk_work_days_config 
        FOREIGN KEY (system_configuration_id) 
        REFERENCES system_configuration(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla para métodos de pago del sistema
CREATE TABLE IF NOT EXISTS system_payment_methods (
    system_configuration_id BIGINT NOT NULL,
    payment_method_type VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (system_configuration_id, payment_method_type),
    CONSTRAINT fk_payment_methods_config 
        FOREIGN KEY (system_configuration_id) 
        REFERENCES system_configuration(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de horarios de atención por día
CREATE TABLE IF NOT EXISTS business_hours (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    system_configuration_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_business_hours_day (system_configuration_id, day_of_week),
    CONSTRAINT fk_business_hours_config 
        FOREIGN KEY (system_configuration_id) 
        REFERENCES system_configuration(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabla de redes sociales
CREATE TABLE IF NOT EXISTS social_networks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    system_configuration_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    url VARCHAR(500) NOT NULL,
    icon VARCHAR(100),
    display_order INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_social_networks_config 
        FOREIGN KEY (system_configuration_id) 
        REFERENCES system_configuration(id) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Crear índices para mejorar el rendimiento
CREATE INDEX idx_business_hours_day ON business_hours(day_of_week);
CREATE INDEX idx_social_networks_active ON social_networks(active);
CREATE INDEX idx_social_networks_order ON social_networks(display_order);

-- Insertar configuración inicial por defecto (solo si no existe)
INSERT INTO system_configuration (restaurant_name, slogan, address, phone, email, tax_rate)
SELECT 'El Gran Sazón', 'El mejor sabor de la ciudad', 'Dirección por configurar', '0000-0000', 'contacto@elgransazon.com', 16.00
WHERE NOT EXISTS (SELECT 1 FROM system_configuration LIMIT 1);

-- Obtener el ID de la configuración (será 1 si acabamos de insertarla)
SET @config_id = (SELECT id FROM system_configuration LIMIT 1);

-- Insertar días laborales por defecto (Lunes a Sábado)
INSERT IGNORE INTO system_work_days (system_configuration_id, day_of_week) VALUES
(@config_id, 'MONDAY'),
(@config_id, 'TUESDAY'),
(@config_id, 'WEDNESDAY'),
(@config_id, 'THURSDAY'),
(@config_id, 'FRIDAY'),
(@config_id, 'SATURDAY');

-- Insertar métodos de pago por defecto (todos activos)
INSERT IGNORE INTO system_payment_methods (system_configuration_id, payment_method_type, enabled) VALUES
(@config_id, 'CASH', TRUE),
(@config_id, 'CREDIT_CARD', TRUE),
(@config_id, 'DEBIT_CARD', TRUE);

-- Insertar horarios por defecto para los días laborales
-- Lunes a Viernes: 8:00 AM - 8:00 PM
INSERT IGNORE INTO business_hours (system_configuration_id, day_of_week, open_time, close_time, is_closed) VALUES
(@config_id, 'MONDAY', '08:00:00', '20:00:00', FALSE),
(@config_id, 'TUESDAY', '08:00:00', '20:00:00', FALSE),
(@config_id, 'WEDNESDAY', '08:00:00', '20:00:00', FALSE),
(@config_id, 'THURSDAY', '08:00:00', '20:00:00', FALSE),
(@config_id, 'FRIDAY', '08:00:00', '20:00:00', FALSE);

-- Sábado: 8:00 AM - 3:00 PM
INSERT IGNORE INTO business_hours (system_configuration_id, day_of_week, open_time, close_time, is_closed) VALUES
(@config_id, 'SATURDAY', '08:00:00', '15:00:00', FALSE);

-- Ejemplos de redes sociales (comentados para que el usuario las agregue manualmente)
-- INSERT INTO social_networks (system_configuration_id, name, url, icon, display_order, active) VALUES
-- (@config_id, 'Facebook', 'https://facebook.com/elgransazon', 'fab fa-facebook', 1, TRUE),
-- (@config_id, 'Instagram', 'https://instagram.com/elgransazon', 'fab fa-instagram', 2, TRUE),
-- (@config_id, 'Twitter', 'https://twitter.com/elgransazon', 'fab fa-twitter', 3, TRUE);

COMMIT;
