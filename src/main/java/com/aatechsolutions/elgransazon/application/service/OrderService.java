package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for Order management
 */
public interface OrderService {

    // ========== CRUD Operations ==========

    /**
     * Create a new order with order details
     * Validates stock, reserves table, deducts ingredients
     */
    Order create(Order order, List<OrderDetail> orderDetails);

    /**
     * Update an existing order (only if status is PENDING)
     */
    Order update(Long id, Order order, List<OrderDetail> orderDetails);

    /**
     * Cancel an order
     * Returns stock if status is PENDING
     */
    Order cancel(Long id, String username);

    /**
     * Change order status
     */
    Order changeStatus(Long id, OrderStatus newStatus, String username);

    /**
     * Add new items to an existing order
     * This allows customers to order additional items (e.g., dessert after main course)
     * New items will be marked with isNewItem=true and itemStatus=PENDING
     * Only available for DINE_IN orders
     */
    Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username);

    /**
     * Change status of specific items in an order
     * Used by chef to update individual item statuses
     */
    Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username);

    /**
     * Delete a specific item from an order
     * Only allows deleting items that are not DELIVERED
     * Returns stock automatically if item is PENDING or (READY and !requiresPreparation)
     * Returns the deleted OrderDetail for response purposes
     */
    OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username);

    /**
     * Delete an order (soft delete or physical based on status)
     */
    void delete(Long id);

    // ========== Query Operations ==========

    /**
     * Find all orders
     */
    List<Order> findAll();

    /**
     * Find order by ID
     */
    Optional<Order> findById(Long id);

    /**
     * Find order by ID with all details loaded (for editing)
     * Includes employee, table, and order details
     */
    Optional<Order> findByIdWithDetails(Long id);

    /**
     * Find order by order number
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Find all orders by table ID
     */
    List<Order> findByTableId(Long tableId);

    /**
     * Find active order by table ID (only one active order per table)
     */
    Optional<Order> findActiveOrderByTableId(Long tableId);

    /**
     * Find all orders by employee ID
     */
    List<Order> findByEmployeeId(Long employeeId);

    /**
     * Find all orders by status
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Find all orders by order type
     */
    List<Order> findByOrderType(OrderType orderType);

    /**
     * Find today's orders
     */
    List<Order> findTodaysOrders();

    /**
     * Find active orders (not cancelled, not delivered, not paid)
     */
    List<Order> findActiveOrders();

    /**
     * Find orders by date range
     */
    List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    // ========== Validation Operations ==========

    /**
     * Validate if there's enough stock for all items in order details
     * Returns map with item IDs that don't have enough stock
     */
    Map<Long, String> validateStock(List<OrderDetail> orderDetails);

    /**
     * Check if table has an active order
     */
    boolean hasActiveOrder(Long tableId);

    /**
     * Validate if table is available for order
     * Table must be AVAILABLE or RESERVED with isOccupied=false
     */
    boolean isTableAvailableForOrder(Long tableId);

    // ========== Order Number Generation ==========

    /**
     * Generate unique order number
     */
    String generateOrderNumber();

    // ========== Statistics ==========

    /**
     * Count orders by status
     */
    long countByStatus(OrderStatus status);

    /**
     * Count today's orders
     */
    long countTodaysOrders();

    /**
     * Count today's orders by status
     */
    long countTodaysOrdersByStatus(OrderStatus status);

    /**
     * Get today's revenue
     */
    BigDecimal getTodaysRevenue();

    /**
     * Find order by ID or throw exception
     */
    Order findByIdOrThrow(Long id);
}
