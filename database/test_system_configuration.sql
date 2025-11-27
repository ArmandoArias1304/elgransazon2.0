-- Script para insertar/actualizar datos de prueba en SystemConfiguration
-- Ejecutar este script si la landing page no muestra datos

-- Verificar si existe configuración
SELECT * FROM system_configuration;

-- Si NO existe, insertar configuración inicial
INSERT INTO system_configuration 
(restaurant_name, slogan, logo_url, address, phone, email, tax_rate, average_consumption_time_minutes, created_at)
VALUES 
('El Gran Sazón', 
 'Sabor auténtico, experiencia inolvidable', 
 'https://via.placeholder.com/150', 
 'Calle Principal #123, Ciudad de México, CDMX 01234', 
 '+52 55 1234 5678', 
 'contacto@elgransazon.com', 
 16.00, 
 120, 
 NOW())
ON DUPLICATE KEY UPDATE
restaurant_name = 'El Gran Sazón',
slogan = 'Sabor auténtico, experiencia inolvidable',
logo_url = 'https://via.placeholder.com/150',
address = 'Calle Principal #123, Ciudad de México, CDMX 01234',
phone = '+52 55 1234 5678',
email = 'contacto@elgransazon.com';

-- Si YA existe, actualizar configuración
UPDATE system_configuration 
SET 
    restaurant_name = 'El Gran Sazón',
    slogan = 'Sabor auténtico, experiencia inolvidable',
    logo_url = 'https://via.placeholder.com/150',
    address = 'Calle Principal #123, Ciudad de México, CDMX 01234',
    phone = '+52 55 1234 5678',
    email = 'contacto@elgransazon.com',
    updated_at = NOW()
WHERE id = 1;

-- Verificar redes sociales
SELECT * FROM social_networks;

-- Eliminar redes sociales existentes (opcional)
-- DELETE FROM social_networks;

-- Insertar redes sociales de ejemplo
INSERT INTO social_networks (name, url, icon, display_order, active, system_configuration_id, created_at)
VALUES 
('Facebook', 'https://facebook.com/elgransazon', 'fab fa-facebook-f', 1, true, 1, NOW()),
('Instagram', 'https://instagram.com/elgransazon', 'fab fa-instagram', 2, true, 1, NOW()),
('WhatsApp', 'https://wa.me/5215512345678', 'fab fa-whatsapp', 3, true, 1, NOW()),
('Twitter', 'https://twitter.com/elgransazon', 'fab fa-twitter', 4, true, 1, NOW())
ON DUPLICATE KEY UPDATE
url = VALUES(url),
icon = VALUES(icon),
active = VALUES(active);

-- Verificar business hours
SELECT * FROM business_hours;

-- Si NO existen, insertar horarios de ejemplo
INSERT INTO business_hours (day_of_week, open_time, close_time, is_closed, system_configuration_id)
VALUES 
('MONDAY', '09:00:00', '22:00:00', false, 1),
('TUESDAY', '09:00:00', '22:00:00', false, 1),
('WEDNESDAY', '09:00:00', '22:00:00', false, 1),
('THURSDAY', '09:00:00', '22:00:00', false, 1),
('FRIDAY', '09:00:00', '23:00:00', false, 1),
('SATURDAY', '10:00:00', '23:00:00', false, 1),
('SUNDAY', '10:00:00', '21:00:00', false, 1)
ON DUPLICATE KEY UPDATE
open_time = VALUES(open_time),
close_time = VALUES(close_time),
is_closed = VALUES(is_closed);

-- Verificar payment methods
SELECT * FROM system_payment_methods;

-- Si NO existen, insertar métodos de pago
INSERT INTO system_payment_methods (system_configuration_id, payment_method_type, enabled)
VALUES 
(1, 'CASH', true),
(1, 'CREDIT_CARD', true),
(1, 'DEBIT_CARD', true),
(1, 'TRANSFER', true)
ON DUPLICATE KEY UPDATE
enabled = VALUES(enabled);

-- Verificar todo
SELECT 'SYSTEM CONFIGURATION' as table_name, COUNT(*) as count FROM system_configuration
UNION ALL
SELECT 'BUSINESS HOURS', COUNT(*) FROM business_hours
UNION ALL
SELECT 'SOCIAL NETWORKS', COUNT(*) FROM social_networks
UNION ALL
SELECT 'PAYMENT METHODS', COUNT(*) FROM system_payment_methods;
