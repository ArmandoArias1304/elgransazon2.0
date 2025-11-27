-- ============================================================================
-- Script de inicialización para la tabla CATEGORIES
-- Restaurant POS System - El Gran Sazón
-- ============================================================================

-- Crear tabla de categorías
CREATE TABLE IF NOT EXISTS categories (
    id_category BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT,
    icon VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    INDEX idx_category_name (name),
    INDEX idx_category_active (active),
    INDEX idx_category_display_order (display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insertar categorías iniciales del menú
INSERT INTO categories (name, description, active, display_order, icon) VALUES
('Entradas', 'Platillos para comenzar la comida', TRUE, 1, 'restaurant'),
('Sopas y Caldos', 'Sopas calientes y reconfortantes', TRUE, 2, 'soup_kitchen'),
('Ensaladas', 'Ensaladas frescas y saludables', TRUE, 3, 'nutrition'),
('Platos Fuertes', 'Platillos principales del menú', TRUE, 4, 'restaurant_menu'),
('Carnes', 'Cortes y preparaciones de carne', TRUE, 5, 'kebab_dining'),
('Mariscos', 'Productos frescos del mar', TRUE, 6, 'set_meal'),
('Pastas', 'Pastas italianas y variadas', TRUE, 7, 'ramen_dining'),
('Pizzas', 'Pizzas al horno tradicionales', TRUE, 8, 'local_pizza'),
('Postres', 'Dulces y postres caseros', TRUE, 9, 'cake'),
('Bebidas Frías', 'Bebidas refrescantes sin alcohol', TRUE, 10, 'local_cafe'),
('Bebidas Calientes', 'Café, té y bebidas calientes', TRUE, 11, 'coffee'),
('Jugos y Licuados', 'Jugos naturales y licuados', TRUE, 12, 'blender'),
('Cervezas', 'Variedad de cervezas nacionales e importadas', TRUE, 13, 'sports_bar'),
('Vinos', 'Selección de vinos tintos, blancos y rosados', TRUE, 14, 'wine_bar'),
('Cócteles', 'Bebidas preparadas con y sin alcohol', TRUE, 15, 'local_bar');

-- Verificar inserción
SELECT 
    id_category,
    name,
    description,
    active,
    display_order,
    icon,
    created_at
FROM categories
ORDER BY display_order;
