-- Script para arreglar números de orden duplicados
-- Ejecutar este script si hay pedidos con números duplicados

-- 1. Ver si hay números de orden duplicados
SELECT 
    order_number, 
    COUNT(*) as count,
    GROUP_CONCAT(id_order) as order_ids
FROM orders
GROUP BY order_number
HAVING COUNT(*) > 1;

-- 2. Si hay duplicados, esta query muestra los detalles
SELECT 
    id_order,
    order_number,
    created_at,
    status,
    total
FROM orders
WHERE order_number IN (
    SELECT order_number
    FROM orders
    GROUP BY order_number
    HAVING COUNT(*) > 1
)
ORDER BY order_number, created_at;

-- 3. OPCIÓN A: Eliminar pedidos duplicados (MANTENER SOLO EL PRIMERO)
-- NOTA: Ejecutar solo si es necesario y después de revisar los datos
-- DELETE FROM orders
-- WHERE id_order IN (
--     SELECT id_order FROM (
--         SELECT id_order,
--                ROW_NUMBER() OVER (PARTITION BY order_number ORDER BY created_at) as rn
--         FROM orders
--     ) t
--     WHERE rn > 1
-- );

-- 4. OPCIÓN B: Renumerar pedidos duplicados con nuevos números únicos
-- NOTA: Esta opción es más segura, no elimina datos
-- UPDATE orders o
-- SET order_number = CONCAT(
--     order_number, 
--     '-DUP', 
--     (
--         SELECT COUNT(*) 
--         FROM orders o2 
--         WHERE o2.order_number = o.order_number 
--         AND o2.id_order < o.id_order
--     )
-- )
-- WHERE order_number IN (
--     SELECT order_number
--     FROM (
--         SELECT order_number
--         FROM orders
--         GROUP BY order_number
--         HAVING COUNT(*) > 1
--     ) duplicates
-- )
-- AND id_order NOT IN (
--     SELECT MIN(id_order)
--     FROM orders
--     GROUP BY order_number
-- );

-- 5. Verificar que ya no hay duplicados
-- SELECT 
--     order_number, 
--     COUNT(*) as count
-- FROM orders
-- GROUP BY order_number
-- HAVING COUNT(*) > 1;
