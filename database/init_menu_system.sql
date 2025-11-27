-- =====================================================
-- MENU SYSTEM INITIALIZATION
-- Tables: presentations, item_menu, item_ingredients
-- =====================================================

-- 1. Create presentations table
CREATE TABLE IF NOT EXISTS presentations (
    id_presentation BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    abbreviation VARCHAR(20),
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    id_category BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_presentation_category FOREIGN KEY (id_category) 
        REFERENCES categories(id_category) ON DELETE CASCADE,
    CONSTRAINT uk_presentation_name_category UNIQUE (name, id_category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create item_menu table
CREATE TABLE IF NOT EXISTS item_menu (
    id_item_menu BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    image_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    id_category BIGINT NOT NULL,
    id_presentation BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_item_menu_category FOREIGN KEY (id_category) 
        REFERENCES categories(id_category) ON DELETE RESTRICT,
    CONSTRAINT fk_item_menu_presentation FOREIGN KEY (id_presentation) 
        REFERENCES presentations(id_presentation) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Create item_ingredients table (Recipe table)
CREATE TABLE IF NOT EXISTS item_ingredients (
    id_item_ingredient BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_item_menu BIGINT NOT NULL,
    id_ingredient BIGINT NOT NULL,
    quantity DECIMAL(10, 3) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_item_ingredient_item_menu FOREIGN KEY (id_item_menu) 
        REFERENCES item_menu(id_item_menu) ON DELETE CASCADE,
    CONSTRAINT fk_item_ingredient_ingredient FOREIGN KEY (id_ingredient) 
        REFERENCES ingredients(id_ingredient) ON DELETE RESTRICT,
    CONSTRAINT uk_item_ingredient UNIQUE (id_item_menu, id_ingredient)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Create indexes for better performance
CREATE INDEX idx_presentation_category ON presentations(id_category);
CREATE INDEX idx_presentation_active ON presentations(active);

CREATE INDEX idx_item_menu_category ON item_menu(id_category);
CREATE INDEX idx_item_menu_presentation ON item_menu(id_presentation);
CREATE INDEX idx_item_menu_active ON item_menu(active);
CREATE INDEX idx_item_menu_available ON item_menu(available);
CREATE INDEX idx_item_menu_active_available ON item_menu(active, available);

CREATE INDEX idx_item_ingredients_item_menu ON item_ingredients(id_item_menu);
CREATE INDEX idx_item_ingredients_ingredient ON item_ingredients(id_ingredient);

-- =====================================================
-- SAMPLE DATA
-- =====================================================

-- Insert sample presentations for existing categories
-- Assuming you have categories with IDs 1-4

-- For category 1 (example: Carnes/Main Courses)
INSERT INTO presentations (name, abbreviation, description, id_category, active) VALUES
('Por Pieza', 'pz', 'Venta por unidad individual', 1, TRUE),
('Por 500g', '500g', 'Venta por medio kilo', 1, TRUE),
('Por Kilo', 'kg', 'Venta por kilogramo', 1, TRUE);

-- For category 2 (example: Bebidas/Beverages)
INSERT INTO presentations (name, abbreviation, description, id_category, active) VALUES
('Vaso 355ml', '355ml', 'Vaso pequeño estándar', 2, TRUE),
('Botella 1L', '1L', 'Botella de un litro', 2, TRUE),
('Jarra 2L', '2L', 'Jarra grande de dos litros', 2, TRUE);

-- For category 3 (example: Postres/Desserts)
INSERT INTO presentations (name, abbreviation, description, id_category, active) VALUES
('Porción Individual', 'ind', 'Porción para una persona', 3, TRUE),
('Porción Grande', 'grande', 'Porción para compartir', 3, TRUE);

-- For category 4 (example: Entradas/Appetizers)
INSERT INTO presentations (name, abbreviation, description, id_category, active) VALUES
('Orden Chica', 'chica', 'Orden pequeña', 4, TRUE),
('Orden Mediana', 'med', 'Orden mediana', 4, TRUE),
('Orden Grande', 'grande', 'Orden grande', 4, TRUE);

-- =====================================================
-- SAMPLE MENU ITEMS WITH RECIPES
-- =====================================================

-- Example 1: Hamburguesa Clásica
-- Assuming: 
--   - Category ID 1 (Carnes)
--   - Presentation ID 1 (Por Pieza)
--   - Ingredients exist: Carne (ID 1), Lechuga (ID 2), Tomate (ID 3), Pan (ID 4)

INSERT INTO item_menu (name, description, price, id_category, id_presentation, active, available) VALUES
('Hamburguesa Clásica', 
 'Deliciosa hamburguesa con carne de res, lechuga fresca, tomate y queso', 
 120.00, 
 1, -- id_category
 1, -- id_presentation (Por Pieza)
 TRUE, 
 TRUE);

-- Recipe for Hamburguesa Clásica (assuming item_menu ID 1)
-- Note: Adjust ingredient IDs based on your actual data
INSERT INTO item_ingredients (id_item_menu, id_ingredient, quantity, unit) VALUES
(1, 1, 1.000, 'unidades'),    -- 1 pieza de carne
(1, 2, 0.030, 'kg'),           -- 30g de lechuga
(1, 3, 0.050, 'kg'),           -- 50g de tomate
(1, 4, 1.000, 'unidades');     -- 1 pan

-- Example 2: Coca-Cola
-- Assuming:
--   - Category ID 2 (Bebidas)
--   - Presentation ID 4 (Vaso 355ml)
--   - Ingredient: Coca-Cola 355ml (ID 5)

INSERT INTO item_menu (name, description, price, id_category, id_presentation, active, available) VALUES
('Coca-Cola', 
 'Refresco Coca-Cola en vaso de 355ml', 
 25.00, 
 2, -- id_category
 4, -- id_presentation (Vaso 355ml)
 TRUE, 
 TRUE);

-- Recipe for Coca-Cola (assuming item_menu ID 2)
INSERT INTO item_ingredients (id_item_menu, id_ingredient, quantity, unit) VALUES
(2, 5, 1.000, 'unidades');     -- 1 botella de Coca-Cola 355ml

-- =====================================================
-- VERIFICATION QUERIES
-- =====================================================

-- Check presentations by category
-- SELECT c.name AS categoria, p.name AS presentacion, p.abbreviation, p.active
-- FROM presentations p
-- INNER JOIN categories c ON p.id_category = c.id_category
-- ORDER BY c.name, p.name;

-- Check menu items with their categories and presentations
-- SELECT 
--     i.id_item_menu,
--     i.name AS item,
--     i.price,
--     c.name AS categoria,
--     p.name AS presentacion,
--     i.active,
--     i.available
-- FROM item_menu i
-- INNER JOIN categories c ON i.id_category = c.id_category
-- INNER JOIN presentations p ON i.id_presentation = p.id_presentation
-- ORDER BY c.name, i.name;

-- Check recipes (ingredients per menu item)
-- SELECT 
--     im.name AS platillo,
--     ing.name AS ingrediente,
--     ii.quantity,
--     ii.unit,
--     ing.current_stock AS stock_disponible
-- FROM item_ingredients ii
-- INNER JOIN item_menu im ON ii.id_item_menu = im.id_item_menu
-- INNER JOIN ingredients ing ON ii.id_ingredient = ing.id_ingredient
-- ORDER BY im.name, ing.name;

-- Check which items can be prepared with current stock
-- SELECT 
--     im.name AS platillo,
--     im.available AS disponible,
--     COUNT(ii.id_item_ingredient) AS num_ingredientes
-- FROM item_menu im
-- LEFT JOIN item_ingredients ii ON im.id_item_menu = ii.id_item_menu
-- GROUP BY im.id_item_menu, im.name, im.available
-- ORDER BY im.name;
