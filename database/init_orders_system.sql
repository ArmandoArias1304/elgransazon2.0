-- =====================================================
-- ORDERS SYSTEM - Database Schema
-- =====================================================
-- Author: AA Tech Solutions
-- Date: 2025-10-23
-- Description: Creates tables for orders management system
-- =====================================================

USE bd_restaurant;

-- =====================================================
-- TABLE: orders
-- Description: Main orders table
-- =====================================================
CREATE TABLE IF NOT EXISTS orders (
    id_order BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE COMMENT 'Unique order number (ORD-20251023-001)',
    order_type VARCHAR(20) NOT NULL COMMENT 'DINE_IN, TAKEOUT, DELIVERY',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, IN_PREPARATION, READY, ON_THE_WAY, DELIVERED, CANCELLED, PAID',
    
    -- Customer Information
    customer_name VARCHAR(100) NOT NULL COMMENT 'Customer name',
    customer_phone VARCHAR(20) NOT NULL COMMENT 'Customer phone number',
    delivery_address VARCHAR(500) NOT NULL COMMENT 'Delivery address or location',
    delivery_references VARCHAR(500) COMMENT 'Additional delivery references',
    
    -- Relationships
    id_table BIGINT NOT NULL COMMENT 'Table ID',
    id_employee BIGINT NOT NULL COMMENT 'Employee (waiter) who created the order',
    
    -- Payment
    payment_method VARCHAR(20) NOT NULL COMMENT 'CASH, CREDIT_CARD, DEBIT_CARD, TRANSFER',
    
    -- Calculations
    subtotal DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Sum of all order details',
    tax_rate DECIMAL(5, 2) NOT NULL COMMENT 'Tax rate percentage (e.g., 16.00)',
    tax_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Calculated tax amount',
    total DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'Subtotal + Tax',
    
    -- Audit Fields
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Order creation timestamp',
    updated_at DATETIME NULL COMMENT 'Last update timestamp',
    created_by VARCHAR(100) NOT NULL COMMENT 'Username who created the order',
    updated_by VARCHAR(100) NULL COMMENT 'Username who last updated the order',
    cancelled_at DATETIME NULL COMMENT 'Cancellation timestamp',
    
    -- Foreign Keys
    CONSTRAINT fk_orders_table FOREIGN KEY (id_table) REFERENCES tables(id_table),
    CONSTRAINT fk_orders_employee FOREIGN KEY (id_employee) REFERENCES employees(id_empleado),
    
    -- Indexes for performance
    INDEX idx_orders_order_number (order_number),
    INDEX idx_orders_status (status),
    INDEX idx_orders_table (id_table),
    INDEX idx_orders_employee (id_employee),
    INDEX idx_orders_created_at (created_at),
    INDEX idx_orders_order_type (order_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Customer orders table';

-- =====================================================
-- TABLE: order_details
-- Description: Order line items (products in each order)
-- =====================================================
CREATE TABLE IF NOT EXISTS order_details (
    id_order_detail BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Relationships
    id_order BIGINT NOT NULL COMMENT 'Order ID',
    id_item_menu BIGINT NOT NULL COMMENT 'Menu item ID',
    
    -- Quantity and Pricing
    quantity INT NOT NULL COMMENT 'Number of items ordered',
    unit_price DECIMAL(10, 2) NOT NULL COMMENT 'Price per unit at order time',
    subtotal DECIMAL(10, 2) NOT NULL COMMENT 'quantity * unit_price',
    
    -- Special Instructions
    comments VARCHAR(500) NULL COMMENT 'Special instructions (e.g., no onions)',
    
    -- Audit
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Detail creation timestamp',
    
    -- Foreign Keys
    CONSTRAINT fk_order_details_order FOREIGN KEY (id_order) 
        REFERENCES orders(id_order) ON DELETE CASCADE,
    CONSTRAINT fk_order_details_item_menu FOREIGN KEY (id_item_menu) 
        REFERENCES item_menu(id_item_menu),
    
    -- Indexes
    INDEX idx_order_details_order (id_order),
    INDEX idx_order_details_item_menu (id_item_menu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order line items table';

-- =====================================================
-- SAMPLE DATA (Optional - for testing)
-- =====================================================

-- Note: Sample data should be added after having:
-- 1. Active tables in the system
-- 2. Active employees
-- 3. Active menu items with available stock
-- 4. System configuration with tax rate

-- Example queries to verify:
-- SELECT * FROM orders ORDER BY created_at DESC;
-- SELECT * FROM order_details WHERE id_order = 1;
-- SELECT o.order_number, o.status, t.table_number, e.nombre, e.apellido
-- FROM orders o
-- JOIN tables t ON o.id_table = t.id_table
-- JOIN employees e ON o.id_employee = e.id_empleado
-- ORDER BY o.created_at DESC;

COMMIT;
