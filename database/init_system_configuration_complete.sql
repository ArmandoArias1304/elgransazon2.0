-- =====================================================
-- Script completo para inicializar SystemConfiguration
-- Incluye: Configuración, días de trabajo, métodos de pago y horarios
-- =====================================================

-- 1. Limpiar datos existentes (opcional, comentar si no quieres borrar)
DELETE FROM business_hours WHERE system_configuration_id IS NOT NULL;
DELETE FROM system_work_days WHERE system_configuration_id IS NOT NULL;
DELETE FROM system_payment_methods WHERE system_configuration_id IS NOT NULL;
DELETE FROM social_networks WHERE system_configuration_id IS NOT NULL;
DELETE FROM system_configuration WHERE id IS NOT NULL;

-- 2. Insertar configuración del sistema
INSERT INTO system_configuration (
    id,
    restaurant_name,
    slogan,
    logo_url,
    address,
    phone,
    email,
    tax_rate,
    average_consumption_time_minutes,
    created_at
) VALUES (
    1,
    'El Gran Sazón',
    'El mejor sabor de la ciudad',
    'https://example.com/logo.png',
    'Calle Principal #123, Ciudad de México',
    '+52 55 1234-5678',
    'contacto@elgransazon.com',
    16.00,
    120,  -- 2 horas
    NOW()
);

-- 3. Insertar días de trabajo (Lunes a Sábado)
-- Estos son los días que el restaurante opera
INSERT INTO system_work_days (system_configuration_id, day_of_week) VALUES
(1, 'MONDAY'),
(1, 'TUESDAY'),
(1, 'WEDNESDAY'),
(1, 'THURSDAY'),
(1, 'FRIDAY'),
(1, 'SATURDAY');
-- Nota: Domingo (SUNDAY) NO se incluye porque el restaurante está cerrado

-- 4. Insertar métodos de pago habilitados
INSERT INTO system_payment_methods (system_configuration_id, payment_method_type, enabled) VALUES
(1, 'CASH', true),
(1, 'CREDIT_CARD', true),
(1, 'DEBIT_CARD', true);

-- 5. Insertar horarios de negocio para TODOS los días de la semana
-- Días de trabajo (Lunes a Sábado): Abierto de 8:00 AM a 10:00 PM
INSERT INTO business_hours (system_configuration_id, day_of_week, open_time, close_time, is_closed) VALUES
(1, 'MONDAY', '08:00:00', '22:00:00', false),
(1, 'TUESDAY', '08:00:00', '22:00:00', false),
(1, 'WEDNESDAY', '08:00:00', '22:00:00', false),
(1, 'THURSDAY', '08:00:00', '22:00:00', false),
(1, 'FRIDAY', '08:00:00', '22:00:00', false),
(1, 'SATURDAY', '08:00:00', '22:00:00', false),
-- Domingo: Cerrado
(1, 'SUNDAY', NULL, NULL, true);

-- 6. Verificar que todo se insertó correctamente
SELECT 'System Configuration' as tabla, COUNT(*) as registros FROM system_configuration
UNION ALL
SELECT 'Work Days' as tabla, COUNT(*) as registros FROM system_work_days
UNION ALL
SELECT 'Payment Methods' as tabla, COUNT(*) as registros FROM system_payment_methods
UNION ALL
SELECT 'Business Hours' as tabla, COUNT(*) as registros FROM business_hours;

-- =====================================================
-- Explicación del Flujo:
-- =====================================================
-- 
-- 1. SYSTEM_CONFIGURATION (1 registro)
--    - Información general del restaurante
--    - Tasa de impuestos
--    - Tiempo promedio de consumo
--
-- 2. SYSTEM_WORK_DAYS (6 registros)
--    - Define qué días trabaja el restaurante
--    - Lunes a Sábado (sin Domingo)
--    - Relación: N work_days por 1 configuración
--
-- 3. SYSTEM_PAYMENT_METHODS (3 registros)
--    - Define qué métodos de pago están activos
--    - Efectivo, Tarjeta de Crédito, Tarjeta de Débito
--    - Relación: N payment_methods por 1 configuración
--
-- 4. BUSINESS_HOURS (7 registros)
--    - Define horario de apertura/cierre para CADA día
--    - 6 días con horarios (8:00 AM - 10:00 PM)
--    - 1 día cerrado (Domingo)
--    - Relación: N business_hours por 1 configuración
--
-- =====================================================
-- Notas Importantes:
-- =====================================================
-- 
-- DUPLICACIÓN DE INFORMACIÓN (Problema de Diseño):
-- - system_work_days dice "trabajamos estos días"
-- - business_hours también dice qué días están abiertos
-- - Pueden estar desincronizados
--
-- SOLUCIÓN FUTURA (Refactorización):
-- - Eliminar system_work_days
-- - Usar solo business_hours con is_closed = false
-- - Un día es "de trabajo" si is_closed = false
--
-- =====================================================
