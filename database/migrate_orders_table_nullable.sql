-- Migración: Hacer que id_table sea opcional en la tabla orders
-- Razón: Los pedidos TAKEOUT y DELIVERY no necesitan mesa asignada
-- Solo los pedidos DINE_IN requieren mesa

-- Modificar la columna id_table para permitir valores NULL
ALTER TABLE orders 
MODIFY COLUMN id_table BIGINT NULL;

-- Verificar el cambio
DESCRIBE orders;

-- Verificar pedidos existentes sin mesa (debería estar vacío antes de la migración)
SELECT 
    id_order,
    order_number,
    order_type,
    id_table,
    status
FROM orders
WHERE id_table IS NULL;
