-- phpMyAdmin SQL Dump
-- Sistema POS de Restaurante - Extensión Completa
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Servidor: 127.0.0.1:3306
-- Tiempo de generación: 15-10-2025 a las 01:36:11
-- Versión del servidor: 8.0.39
-- Versión de PHP: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `bd_restaurant`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `categories`
--

CREATE TABLE `categories` (
  `id_category` bigint NOT NULL,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `display_order` int DEFAULT NULL,
  `icon` varchar(50) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb3_spanish_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `employee`
--

CREATE TABLE `employee` (
  `id_empleado` bigint NOT NULL,
  `apellido` varchar(100) COLLATE utf8mb3_spanish_ci NOT NULL,
  `contrasenia` varchar(255) COLLATE utf8mb3_spanish_ci NOT NULL,
  `email` varchar(150) COLLATE utf8mb3_spanish_ci NOT NULL,
  `enabled` bit(1) NOT NULL,
  `nombre` varchar(100) COLLATE utf8mb3_spanish_ci NOT NULL,
  `username` varchar(50) COLLATE utf8mb3_spanish_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `employee_roles`
--

CREATE TABLE `employee_roles` (
  `id_empleado` bigint NOT NULL,
  `id_rol` bigint NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `roles`
--

CREATE TABLE `roles` (
  `id_rol` bigint NOT NULL,
  `nombre_rol` varchar(50) COLLATE utf8mb3_spanish_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `ingredient_categories`
--

CREATE TABLE `ingredient_categories` (
  `id_category` bigint NOT NULL,
  `active` bit(1) NOT NULL,
  `color` varchar(7) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `icon` varchar(50) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb3_spanish_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `ingredients`
-- MODIFICADA: Se agregó el campo is_countable
--

CREATE TABLE `ingredients` (
  `id_ingredient` bigint NOT NULL,
  `active` bit(1) NOT NULL,
  `cost_per_unit` decimal(10,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(3) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `current_stock` decimal(10,3) DEFAULT NULL,
  `description` text COLLATE utf8mb3_spanish_ci,
  `max_stock` decimal(10,3) DEFAULT NULL,
  `min_stock` decimal(10,3) DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb3_spanish_ci NOT NULL,
  `shelf_life_days` int DEFAULT NULL,
  `storage_location` varchar(100) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `unit_of_measure` varchar(20) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `id_category` bigint NOT NULL,
  `is_countable` bit(1) NOT NULL DEFAULT b'1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `suppliers`
--

CREATE TABLE `suppliers` (
  `id_supplier` bigint NOT NULL,
  `active` bit(1) NOT NULL,
  `address` varchar(300) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `contact_person` varchar(100) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(150) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `name` varchar(150) COLLATE utf8mb3_spanish_ci NOT NULL,
  `notes` varchar(500) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `rating` int DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `supplier_ingredient_categories`
--

CREATE TABLE `supplier_ingredient_categories` (
  `id_supplier` bigint NOT NULL,
  `id_category` bigint NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------
-- ============================================
-- NUEVAS TABLAS PARA EL SISTEMA POS
-- ============================================

--
-- Estructura de tabla para la tabla `tables`
--

CREATE TABLE `tables` (
  `id_table` bigint NOT NULL,
  `name` varchar(50) COLLATE utf8mb3_spanish_ci NOT NULL,
  `capacity` int NOT NULL,
  `status` enum('AVAILABLE','OCCUPIED','RESERVED','OUT_OF_SERVICE') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'AVAILABLE',
  `location` varchar(100) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `menu_items`
--

CREATE TABLE `menu_items` (
  `id_menu_item` bigint NOT NULL,
  `name` varchar(150) COLLATE utf8mb3_spanish_ci NOT NULL,
  `sku` varchar(50) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `description` text COLLATE utf8mb3_spanish_ci,
  `price` decimal(10,2) NOT NULL,
  `discount_price` decimal(10,2) DEFAULT NULL,
  `category_id` bigint NOT NULL,
  `image_url` varchar(255) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `is_featured` bit(1) NOT NULL DEFAULT b'0',
  `is_new` bit(1) NOT NULL DEFAULT b'0',
  `cost` decimal(10,2) DEFAULT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `taxable` bit(1) NOT NULL DEFAULT b'1',
  `preparation_time_minutes` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `menu_item_recipes`
--

CREATE TABLE `menu_item_recipes` (
  `id` bigint NOT NULL,
  `menu_item_id` bigint NOT NULL,
  `ingredient_id` bigint NOT NULL,
  `quantity` decimal(10,3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `orders`
--

CREATE TABLE `orders` (
  `id_order` bigint NOT NULL,
  `created_by_employee` bigint NOT NULL,
  `customer_name` varchar(150) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `customer_phone` varchar(20) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `customer_address` varchar(300) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `customer_lat` decimal(10,7) DEFAULT NULL,
  `customer_lng` decimal(10,7) DEFAULT NULL,
  `table_id` bigint DEFAULT NULL,
  `order_type` enum('DINE_IN','TAKEAWAY','DELIVERY') COLLATE utf8mb3_spanish_ci NOT NULL,
  `subtotal` decimal(12,2) NOT NULL DEFAULT '0.00',
  `tax` decimal(12,2) NOT NULL DEFAULT '0.00',
  `delivery_fee` decimal(12,2) DEFAULT '0.00',
  `total` decimal(12,2) NOT NULL DEFAULT '0.00',
  `status` enum('PENDING','CANCELLED','PREPARING','READY_TO_SERVE','DELIVERING','AWAITING_PAYMENT','COMPLETED') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `order_items`
--

CREATE TABLE `order_items` (
  `id_order_item` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `menu_item_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(10,2) NOT NULL,
  `total_price` decimal(12,2) NOT NULL,
  `notes` varchar(500) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `status` enum('PENDING','PREPARING','READY','SERVED','CANCELLED') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'PENDING'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `kitchen_queue`
--

CREATE TABLE `kitchen_queue` (
  `id_kitchen` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `assigned_chef` bigint DEFAULT NULL,
  `status` enum('RECEIVED','ACCEPTED','PREPARING','READY','CANCELLED') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'RECEIVED',
  `accepted_at` datetime(6) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `ready_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `order_management`
--

CREATE TABLE `order_management` (
  `id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `created_by_employee` bigint NOT NULL,
  `visible_to` enum('CREATOR','ALL') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'CREATOR',
  `status` enum('PENDING','IN_PROGRESS','COMPLETED','CANCELLED') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'PENDING',
  `last_update` datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `payments`
--

CREATE TABLE `payments` (
  `id_payment` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `paid_by_employee` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `payment_type` enum('CASH','CARD','TRANSFER') COLLATE utf8mb3_spanish_ci NOT NULL,
  `status` enum('PENDING','APPROVED','REJECTED') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'PENDING',
  `commission_amount` decimal(10,2) DEFAULT NULL,
  `card_terminal_reference` varchar(200) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `sales`
--

CREATE TABLE `sales` (
  `id_sale` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `payment_id` bigint NOT NULL,
  `sale_date` datetime(6) NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` enum('PAID','REFUNDED') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'PAID',
  `invoice_number` varchar(100) COLLATE utf8mb3_spanish_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `reservations`
--

CREATE TABLE `reservations` (
  `id_reservation` bigint NOT NULL,
  `customer_name` varchar(150) COLLATE utf8mb3_spanish_ci NOT NULL,
  `customer_phone` varchar(20) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `table_id` bigint NOT NULL,
  `reservation_start` datetime(6) NOT NULL,
  `reservation_end` datetime(6) NOT NULL,
  `status` enum('PENDING','CONFIRMED','CANCELLED','COMPLETED') COLLATE utf8mb3_spanish_ci NOT NULL DEFAULT 'PENDING',
  `created_by_employee` bigint DEFAULT NULL,
  `notes` varchar(500) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `system_config`
--

CREATE TABLE `system_config` (
  `id_config` bigint NOT NULL,
  `restaurant_name` varchar(200) COLLATE utf8mb3_spanish_ci NOT NULL,
  `slogan` varchar(300) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `logo_url` varchar(255) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `address` varchar(300) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `email` varchar(150) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `social_links` json DEFAULT NULL,
  `work_hours` json DEFAULT NULL,
  `tax_rate` decimal(5,2) NOT NULL DEFAULT '16.00',
  `payment_commissions` json DEFAULT NULL,
  `delivery_distance_tiers` json DEFAULT NULL,
  `max_delivery_km` decimal(5,2) DEFAULT '20.00',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `inventory_movements`
--

CREATE TABLE `inventory_movements` (
  `id_movement` bigint NOT NULL,
  `ingredient_id` bigint NOT NULL,
  `change_quantity` decimal(10,3) NOT NULL,
  `movement_type` enum('PURCHASE','ADJUSTMENT','USAGE','RETURN','WASTE') COLLATE utf8mb3_spanish_ci NOT NULL,
  `related_order_item_id` bigint DEFAULT NULL,
  `created_by_employee` bigint NOT NULL,
  `notes` varchar(500) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `employee_sessions_history`
--

CREATE TABLE `employee_sessions_history` (
  `id_session` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `action` enum('LOGIN','LOGOUT','FORCED_LOGOUT') COLLATE utf8mb3_spanish_ci NOT NULL,
  `ip_address` varchar(45) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `user_agent` varchar(255) COLLATE utf8mb3_spanish_ci DEFAULT NULL,
  `timestamp` datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_spanish_ci;

-- --------------------------------------------------------
-- ============================================
-- CLAVES PRIMARIAS
-- ============================================

ALTER TABLE `categories`
  ADD PRIMARY KEY (`id_category`),
  ADD UNIQUE KEY `UKt8o6pivur7nn124jehx7cygw5` (`name`);

ALTER TABLE `employee`
  ADD PRIMARY KEY (`id_empleado`),
  ADD UNIQUE KEY `UKfopic1oh5oln2khj8eat6ino0` (`email`),
  ADD UNIQUE KEY `UKim8flsuftl52etbhgnr62d6wh` (`username`);

ALTER TABLE `employee_roles`
  ADD PRIMARY KEY (`id_empleado`,`id_rol`),
  ADD KEY `FK9h1bnjwi3idyhibhtg1p0p077` (`id_rol`);

ALTER TABLE `roles`
  ADD PRIMARY KEY (`id_rol`),
  ADD UNIQUE KEY `UK7odo9mpa3aq06bh7o6ri3v5ue` (`nombre_rol`);

ALTER TABLE `ingredient_categories`
  ADD PRIMARY KEY (`id_category`),
  ADD UNIQUE KEY `UKbfqyl6lsngd5cn265g5cwihyl` (`name`),
  ADD KEY `FKnux6dbv43bvofq58rgjomsac4` (`created_by`);

ALTER TABLE `ingredients`
  ADD PRIMARY KEY (`id_ingredient`),
  ADD UNIQUE KEY `UKj6tsl15xx76y4kv41yxr4uxab` (`name`),
  ADD KEY `FK8e42r4wgljalj3oi4wtc1xlmh` (`id_category`),
  ADD KEY `idx_ingredients_countable_stock` (`is_countable`, `current_stock`);

ALTER TABLE `suppliers`
  ADD PRIMARY KEY (`id_supplier`),
  ADD KEY `FKjt98ob7fq1fc742quv2hw6e3l` (`created_by`);

ALTER TABLE `supplier_ingredient_categories`
  ADD PRIMARY KEY (`id_supplier`,`id_category`),
  ADD KEY `FKo7b1et9c6duf8v7oaft88q3j6` (`id_category`);

ALTER TABLE `tables`
  ADD PRIMARY KEY (`id_table`),
  ADD UNIQUE KEY `UKtable_name` (`name`);

ALTER TABLE `menu_items`
  ADD PRIMARY KEY (`id_menu_item`),
  ADD UNIQUE KEY `UKmenu_item_sku` (`sku`),
  ADD KEY `FKmenu_item_category` (`category_id`),
  ADD KEY `idx_menu_items_featured` (`is_featured`),
  ADD KEY `idx_menu_items_new` (`is_new`),
  ADD KEY `idx_menu_items_category_active` (`category_id`, `active`);

ALTER TABLE `menu_item_recipes`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UKmenu_item_ingredient` (`menu_item_id`,`ingredient_id`),
  ADD KEY `FKrecipe_ingredient` (`ingredient_id`);

ALTER TABLE `orders`
  ADD PRIMARY KEY (`id_order`),
  ADD KEY `FKorder_employee` (`created_by_employee`),
  ADD KEY `FKorder_table` (`table_id`),
  ADD KEY `idx_orders_status` (`status`),
  ADD KEY `idx_orders_created_by` (`created_by_employee`),
  ADD KEY `idx_orders_type_status` (`order_type`, `status`);

ALTER TABLE `order_items`
  ADD PRIMARY KEY (`id_order_item`),
  ADD KEY `FKorder_item_order` (`order_id`),
  ADD KEY `FKorder_item_menu` (`menu_item_id`),
  ADD KEY `idx_order_items_status` (`status`);

ALTER TABLE `kitchen_queue`
  ADD PRIMARY KEY (`id_kitchen`),
  ADD KEY `FKkitchen_order` (`order_id`),
  ADD KEY `FKkitchen_order_item` (`order_item_id`),
  ADD KEY `FKkitchen_chef` (`assigned_chef`),
  ADD KEY `idx_kitchen_status_chef` (`status`, `assigned_chef`);

ALTER TABLE `order_management`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UKorder_management_order` (`order_id`),
  ADD KEY `FKorder_mgmt_employee` (`created_by_employee`);

ALTER TABLE `payments`
  ADD PRIMARY KEY (`id_payment`),
  ADD KEY `FKpayment_order` (`order_id`),
  ADD KEY `FKpayment_employee` (`paid_by_employee`),
  ADD KEY `idx_payments_status` (`status`);

ALTER TABLE `sales`
  ADD PRIMARY KEY (`id_sale`),
  ADD UNIQUE KEY `UKsale_order` (`order_id`),
  ADD KEY `FKsale_payment` (`payment_id`),
  ADD KEY `idx_sales_date` (`sale_date`);

ALTER TABLE `reservations`
  ADD PRIMARY KEY (`id_reservation`),
  ADD KEY `FKreservation_table` (`table_id`),
  ADD KEY `FKreservation_employee` (`created_by_employee`),
  ADD KEY `idx_reservations_status` (`status`),
  ADD KEY `idx_reservations_date` (`reservation_start`, `reservation_end`);

ALTER TABLE `system_config`
  ADD PRIMARY KEY (`id_config`);

ALTER TABLE `inventory_movements`
  ADD PRIMARY KEY (`id_movement`),
  ADD KEY `FKmovement_ingredient` (`ingredient_id`),
  ADD KEY `FKmovement_order_item` (`related_order_item_id`),
  ADD KEY `FKmovement_employee` (`created_by_employee`),
  ADD KEY `idx_movements_type_date` (`movement_type`, `created_at`);

ALTER TABLE `employee_sessions_history`
  ADD PRIMARY KEY (`id_session`),
  ADD KEY `FKsession_employee` (`employee_id`),
  ADD KEY `idx_sessions_timestamp` (`timestamp`);

-- --------------------------------------------------------
-- ============================================
-- AUTO_INCREMENT
-- ============================================

ALTER TABLE `categories`
  MODIFY `id_category` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `employee`
  MODIFY `id_empleado` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `roles`
  MODIFY `id_rol` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `ingredient_categories`
  MODIFY `id_category` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `ingredients`
  MODIFY `id_ingredient` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `suppliers`
  MODIFY `id_supplier` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `tables`
  MODIFY `id_table` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `menu_items`
  MODIFY `id_menu_item` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `menu_item_recipes`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `orders`
  MODIFY `id_order` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `order_items`
  MODIFY `id_order_item` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `kitchen_queue`
  MODIFY `id_kitchen` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `order_management`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `payments`
  MODIFY `id_payment` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `sales`
  MODIFY `id_sale` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `reservations`
  MODIFY `id_reservation` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `system_config`
  MODIFY `id_config` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `inventory_movements`
  MODIFY `id_movement` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `employee_sessions_history`
  MODIFY `id_session` bigint NOT NULL AUTO_INCREMENT;

-- --------------------------------------------------------
-- ============================================
-- RESTRICCIONES DE CLAVES FORÁNEAS
-- ============================================

-- Tablas existentes
ALTER TABLE `employee_roles`
  ADD CONSTRAINT `FK9h1bnjwi3idyhibhtg1p0p077` FOREIGN KEY (`id_rol`) REFERENCES `roles` (`id_rol`),
  ADD CONSTRAINT `FKc4y759mxj0wljacraijkbb114` FOREIGN KEY (`id_empleado`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `ingredient_categories`
  ADD CONSTRAINT `FKnux6dbv43bvofq58rgjomsac4` FOREIGN KEY (`created_by`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `ingredients`
  ADD CONSTRAINT `FK8e42r4wgljalj3oi4wtc1xlmh` FOREIGN KEY (`id_category`) REFERENCES `ingredient_categories` (`id_category`);

ALTER TABLE `suppliers`
  ADD CONSTRAINT `FKjt98ob7fq1fc742quv2hw6e3l` FOREIGN KEY (`created_by`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `supplier_ingredient_categories`
  ADD CONSTRAINT `FK2j6h1fx9rdy45d72xtyr9yrsm` FOREIGN KEY (`id_supplier`) REFERENCES `suppliers` (`id_supplier`),
  ADD CONSTRAINT `FKo7b1et9c6duf8v7oaft88q3j6` FOREIGN KEY (`id_category`) REFERENCES `ingredient_categories` (`id_category`);

-- Nuevas tablas
ALTER TABLE `menu_items`
  ADD CONSTRAINT `FKmenu_item_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id_category`);

ALTER TABLE `menu_item_recipes`
  ADD CONSTRAINT `FKrecipe_menu_item` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_items` (`id_menu_item`) ON DELETE CASCADE,
  ADD CONSTRAINT `FKrecipe_ingredient` FOREIGN KEY (`ingredient_id`) REFERENCES `ingredients` (`id_ingredient`);

ALTER TABLE `orders`
  ADD CONSTRAINT `FKorder_employee` FOREIGN KEY (`created_by_employee`) REFERENCES `employee` (`id_empleado`),
  ADD CONSTRAINT `FKorder_table` FOREIGN KEY (`table_id`) REFERENCES `tables` (`id_table`);

ALTER TABLE `order_items`
  ADD CONSTRAINT `FKorder_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id_order`) ON DELETE CASCADE,
  ADD CONSTRAINT `FKorder_item_menu` FOREIGN KEY (`menu_item_id`) REFERENCES `menu_items` (`id_menu_item`);

ALTER TABLE `kitchen_queue`
  ADD CONSTRAINT `FKkitchen_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id_order`) ON DELETE CASCADE,
  ADD CONSTRAINT `FKkitchen_order_item` FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id_order_item`) ON DELETE CASCADE,
  ADD CONSTRAINT `FKkitchen_chef` FOREIGN KEY (`assigned_chef`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `order_management`
  ADD CONSTRAINT `FKorder_mgmt_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id_order`) ON DELETE CASCADE,
  ADD CONSTRAINT `FKorder_mgmt_employee` FOREIGN KEY (`created_by_employee`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `payments`
  ADD CONSTRAINT `FKpayment_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id_order`),
  ADD CONSTRAINT `FKpayment_employee` FOREIGN KEY (`paid_by_employee`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `sales`
  ADD CONSTRAINT `FKsale_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id_order`),
  ADD CONSTRAINT `FKsale_payment` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id_payment`);

ALTER TABLE `reservations`
  ADD CONSTRAINT `FKreservation_table` FOREIGN KEY (`table_id`) REFERENCES `tables` (`id_table`),
  ADD CONSTRAINT `FKreservation_employee` FOREIGN KEY (`created_by_employee`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `inventory_movements`
  ADD CONSTRAINT `FKmovement_ingredient` FOREIGN KEY (`ingredient_id`) REFERENCES `ingredients` (`id_ingredient`),
  ADD CONSTRAINT `FKmovement_order_item` FOREIGN KEY (`related_order_item_id`) REFERENCES `order_items` (`id_order_item`),
  ADD CONSTRAINT `FKmovement_employee` FOREIGN KEY (`created_by_employee`) REFERENCES `employee` (`id_empleado`);

ALTER TABLE `employee_sessions_history`
  ADD CONSTRAINT `FKsession_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id_empleado`);

COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;