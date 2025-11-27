package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.OrderType;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order entity
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Find order by order number
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Find order by ID with all relationships loaded (for editing)
     */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.employee " +
           "LEFT JOIN FETCH o.preparedBy " +
           "LEFT JOIN FETCH o.paidBy " +
           "LEFT JOIN FETCH o.table " +
           "LEFT JOIN FETCH o.orderDetails od " +
           "LEFT JOIN FETCH od.itemMenu " +
           "WHERE o.idOrder = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    /**
     * Find all orders by table
     */
    List<Order> findByTable(RestaurantTable table);

    /**
     * Find all orders by table ID
     */
    @Query("SELECT o FROM Order o WHERE o.table.id = :tableId ORDER BY o.createdAt DESC")
    List<Order> findByTableId(@Param("tableId") Long tableId);

    /**
     * Find active order by table (not cancelled, not delivered)
     */
    @Query("SELECT o FROM Order o WHERE o.table.id = :tableId " +
           "AND o.status NOT IN ('CANCELLED', 'DELIVERED', 'PAID') " +
           "ORDER BY o.createdAt DESC")
    Optional<Order> findActiveOrderByTableId(@Param("tableId") Long tableId);

    /**
     * Find all orders by employee ID
     */
    @Query("SELECT o FROM Order o WHERE o.employee.idEmpleado = :employeeId ORDER BY o.createdAt DESC")
    List<Order> findByEmployeeId(@Param("employeeId") Long employeeId);

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
    @Query("SELECT o FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE ORDER BY o.createdAt DESC")
    List<Order> findTodaysOrders();

    /**
     * Find active orders (not cancelled, not delivered, not paid)
     */
    @Query("SELECT o FROM Order o WHERE o.status NOT IN ('CANCELLED', 'DELIVERED', 'PAID') " +
           "ORDER BY o.createdAt DESC")
    List<Order> findActiveOrders();

    /**
     * Find orders by date range
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Count orders by status
     */
    long countByStatus(OrderStatus status);

    /**
     * Count today's orders
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE")
    long countTodaysOrders();

    /**
     * Count orders for today by status
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE AND o.status = :status")
    long countTodaysOrdersByStatus(@Param("status") OrderStatus status);

    /**
     * Get today's revenue (sum of totals from PAID orders created today)
     */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o " +
           "WHERE DATE(o.createdAt) = CURRENT_DATE AND o.status = 'PAID'")
    java.math.BigDecimal getTodaysRevenue();

    /**
     * Get count of orders created today (for generating order number)
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE")
    long countOrdersCreatedToday();

    /**
     * Find the last order number for today (for generating unique order numbers)
     */
    @Query("SELECT o.orderNumber FROM Order o WHERE DATE(o.createdAt) = CURRENT_DATE " +
           "ORDER BY o.orderNumber DESC LIMIT 1")
    Optional<String> findLastOrderNumberToday();

    /**
     * Check if order number exists
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Find all orders ordered by created date desc
     */
    @Query("SELECT o FROM Order o ORDER BY o.createdAt DESC")
    List<Order> findAllOrderByCreatedAtDesc();

    /**
     * Find all orders with details loaded (for chef filtering)
     * Uses FETCH JOIN to load OrderDetails and ItemMenu in a single query
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.orderDetails od " +
           "LEFT JOIN FETCH od.itemMenu " +
           "ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails();

    /**
     * Find orders that have at least ONE item requiring preparation (Chef view)
     * This query filters at database level instead of loading all orders
     */
    @Query("SELECT DISTINCT o FROM Order o " +
           "JOIN FETCH o.orderDetails od " +
           "JOIN FETCH od.itemMenu im " +
           "WHERE im.requiresPreparation = true " +
           "ORDER BY o.createdAt DESC")
    List<Order> findOrdersWithPreparationItems();

    /**
     * Find all orders by customer ID
     */
    @Query("SELECT o FROM Order o WHERE o.customer.idCustomer = :customerId ORDER BY o.createdAt DESC")
    List<Order> findByCustomerId(@Param("customerId") Long customerId);

    /**
     * Find all orders by customer email
     */
    @Query("SELECT o FROM Order o WHERE o.customer.email = :customerEmail ORDER BY o.createdAt DESC")
    List<Order> findByCustomerEmail(@Param("customerEmail") String customerEmail);
}
