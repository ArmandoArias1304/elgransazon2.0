-- ============================================================================
-- Script de inicialización para la tabla SUPPLIERS
-- Restaurant POS System - El Gran Sazón
-- ============================================================================

-- Crear tabla de proveedores
CREATE TABLE IF NOT EXISTS suppliers (
    id_supplier BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    contact_person VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(150),
    address VARCHAR(300),
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    rating INT CHECK (rating >= 1 AND rating <= 5),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    INDEX idx_supplier_name (name),
    INDEX idx_supplier_active (active),
    INDEX idx_supplier_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insertar proveedores iniciales
INSERT INTO suppliers (name, contact_person, phone, email, address, notes, active, rating) VALUES
('Distribuidora La Costa', 'María González', '+1 234 567 8901', 'contacto@lacosta.com', 'Av. Principal 123, Ciudad', 'Proveedor principal de mariscos frescos', TRUE, 5),
('Frutas y Verduras del Valle', 'José Martínez', '+1 234 567 8902', 'ventas@frutasdelvalle.com', 'Calle Comercio 456, Ciudad', 'Productos orgánicos y frescos', TRUE, 5),
('Carnes Selectas Premium', 'Carlos Rodríguez', '+1 234 567 8903', 'pedidos@carnesselectas.com', 'Zona Industrial Lote 10, Ciudad', 'Cortes de carne de alta calidad', TRUE, 4),
('Lácteos La Granja', 'Ana Pérez', '+1 234 567 8904', 'info@lacteos-granja.com', 'Km 15 Carretera Norte, Ciudad', 'Productos lácteos artesanales', TRUE, 5),
('Especias y Condimentos El Sabor', 'Luis Torres', '+1 234 567 8905', 'contacto@especiaselsabor.com', 'Mercado Central Local 45, Ciudad', 'Especias importadas y nacionales', TRUE, 4),
('Panadería Artesanal Don Pan', 'Roberto Sánchez', '+1 234 567 8906', 'pedidos@donpan.com', 'Av. Libertad 789, Ciudad', 'Pan fresco diario y productos de repostería', TRUE, 4),
('Bebidas y Licores La Bodega', 'Patricia López', '+1 234 567 8907', 'ventas@labodega.com', 'Centro Comercial Plaza Local 12, Ciudad', 'Amplio catálogo de bebidas', TRUE, 5),
('Proveedor General Alimenticio', 'Fernando Díaz', '+1 234 567 8908', 'contacto@proveedorgeneral.com', 'Polígono Industrial 234, Ciudad', 'Productos secos y enlatados', TRUE, 3),
('Aceites y Vinagres La Oliva', 'Carmen Ruiz', '+1 234 567 8909', 'info@laoliva.com', 'Calle Mayor 567, Ciudad', 'Aceites premium y vinagres gourmet', TRUE, 5),
('Marisquería del Puerto', 'Manuel Castro', '+1 234 567 8910', 'pedidos@marisqueriapuerto.com', 'Muelle Pesquero 1, Puerto', 'Mariscos y pescados del día', TRUE, 4),
('Distribuidora de Pastas Italiana', 'Giovanni Rossi', '+1 234 567 8911', 'ventas@pastaitaliana.com', 'Av. Italia 890, Ciudad', 'Pastas frescas y secas importadas', TRUE, 4),
('Congelados y Refrigerados', 'Sandra Morales', '+1 234 567 8912', 'contacto@congeladosrefrigerados.com', 'Zona Franca Bodega 5, Ciudad', 'Productos congelados de calidad', TRUE, 3),
('Sin Proveedor Asignado', NULL, NULL, NULL, NULL, 'Proveedor por defecto para ingredientes sin proveedor específico', TRUE, NULL);

-- Verificar inserción
SELECT 
    id_supplier,
    name,
    contact_person,
    phone,
    email,
    rating,
    active,
    created_at
FROM suppliers
ORDER BY name;
