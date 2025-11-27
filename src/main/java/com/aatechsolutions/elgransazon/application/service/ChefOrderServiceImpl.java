package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ChefOrderServiceImpl - Implementation for Chef role
 * 
 * Restrictions:
 * - Can only change status from PENDING to IN_PREPARATION
 * - Can only change status from IN_PREPARATION to READY
 * - Can see all PENDING orders to choose which ones to prepare
 * - Can see orders they have accepted (IN_PREPARATION or READY)
 * - Cannot cancel orders
 * - Cannot create new orders
 * - Cannot mark orders as DELIVERED or PAID
 */
@Service("chefOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChefOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService; // Delegate to admin service for actual operations
    private final OrderRepository orderRepository; // Direct access for optimized queries

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Validate if chef can change to this status
     * Chef can only change PENDING -> IN_PREPARATION or IN_PREPARATION -> READY
     */
    private void validateStatusChange(Order order, OrderStatus newStatus) {
        if (order.getStatus() == OrderStatus.PENDING && newStatus == OrderStatus.IN_PREPARATION) {
            // Valid: Chef accepts the order
            return;
        }
        if (order.getStatus() == OrderStatus.IN_PREPARATION && newStatus == OrderStatus.READY) {
            // Valid: Chef finishes preparing the order
            return;
        }
        throw new IllegalStateException(
            "El chef solo puede cambiar el estado de PENDIENTE a EN PREPARACIÓN o de EN PREPARACIÓN a LISTO"
        );
    }

    // ========== CRUD Operations (restricted for chef) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El chef no puede crear pedidos");
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El chef no puede modificar pedidos");
    }

    @Override
    public Order cancel(Long id, String username) {
        throw new UnsupportedOperationException("El chef no puede cancelar pedidos");
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(id);
        String currentUsername = getCurrentUsername();
        
        // Validate that chef can only change status of orders they accepted
        // Exception: PENDING orders can be accepted by any chef
        if (order.getStatus() == OrderStatus.IN_PREPARATION) {
            if (order.getPreparedBy() == null || 
                !order.getPreparedBy().getUsername().equalsIgnoreCase(currentUsername)) {
                throw new IllegalStateException(
                    "Solo el chef que aceptó esta orden puede cambiar su estado"
                );
            }
        }
        
        validateStatusChange(order, newStatus);
        log.info("Chef {} changing order {} status from {} to {}", 
            currentUsername, id, order.getStatus(), newStatus);
        
        // When chef accepts the order (PENDING -> IN_PREPARATION), set preparedBy
        if (newStatus == OrderStatus.IN_PREPARATION && order.getPreparedBy() == null) {
            // Find the current chef employee
            order.setPreparedBy(order.getEmployee()); // Will be set properly in controller
            log.info("Chef {} accepted order {}", currentUsername, id);
        }
        
        return adminOrderService.changeStatus(id, newStatus, username);
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        throw new UnsupportedOperationException("El chef no puede agregar items a pedidos");
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(orderId);
        String currentUsername = getCurrentUsername();
        
        // Validate that chef can change these items
        for (Long itemDetailId : itemDetailIds) {
            OrderDetail detail = order.getOrderDetails().stream()
                .filter(d -> d.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item detail no encontrado: " + itemDetailId
                ));
            
            // Check if item is in preparation by another chef
            if (detail.getItemStatus() == OrderStatus.IN_PREPARATION) {
                if (detail.getPreparedBy() != null && 
                    !detail.getPreparedBy().equals(currentUsername)) {
                    throw new IllegalStateException(
                        "Solo el chef que aceptó este item puede cambiar su estado: " + 
                        detail.getItemMenu().getName()
                    );
                }
            }
            
            // Validate status change for this item
            OrderStatus itemStatus = detail.getItemStatus();
            if (!(itemStatus == OrderStatus.PENDING && newStatus == OrderStatus.IN_PREPARATION) &&
                !(itemStatus == OrderStatus.IN_PREPARATION && newStatus == OrderStatus.READY)) {
                throw new IllegalStateException(
                    "El chef solo puede cambiar items de PENDIENTE a EN PREPARACIÓN o de EN PREPARACIÓN a LISTO"
                );
            }
        }
        
        log.info("Chef {} changing status of {} items in order {}", 
            currentUsername, itemDetailIds.size(), orderId);
        
        return adminOrderService.changeItemsStatus(orderId, itemDetailIds, newStatus, username);
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("El chef no puede eliminar pedidos");
    }

    // ========== Query Operations (filtered for chef) ==========

    @Override
    public List<Order> findAll() {
        // Chef ONLY sees orders that contain at least ONE item requiring preparation
        // Orders with ONLY items that don't require preparation (like sodas) are filtered out
        // NOW OPTIMIZED: Filter at database level instead of loading all orders
        log.info("🔍 Chef findAll() - Loading orders with preparation items (DB-level filter)");
        List<Order> ordersWithPreparation = orderRepository.findOrdersWithPreparationItems();
        log.info("🔍 Orders visible to chef: {}", ordersWithPreparation.size());
        
        return ordersWithPreparation;
    }

    /**
     * Check if an order has at least one item that requires chef preparation
     * @param order The order to check
     * @return true if at least one item requires preparation, false otherwise
     * 
     * DEPRECATED: Now filtering is done at database level via findOrdersWithPreparationItems()
     * Keeping this method for reference or if needed for other use cases
     */
    @Deprecated
    private boolean hasItemsRequiringPreparation(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            log.debug("Order {} has no details", order.getIdOrder());
            return false;
        }
        
        boolean hasPreparationItems = order.getOrderDetails().stream()
            .anyMatch(detail -> {
                if (detail.getItemMenu() == null) {
                    log.warn("OrderDetail {} has null ItemMenu", detail.getIdOrderDetail());
                    return false;
                }
                Boolean requiresPrep = detail.getItemMenu().getRequiresPreparation();
                log.debug("Order {}, Item '{}': requiresPreparation = {}", 
                    order.getOrderNumber(), 
                    detail.getItemMenu().getName(), 
                    requiresPrep);
                return Boolean.TRUE.equals(requiresPrep);
            });
        
        log.info("🔍 Order {} hasItemsRequiringPreparation: {}", order.getOrderNumber(), hasPreparationItems);
        return hasPreparationItems;
    }

    @Override
    public Optional<Order> findById(Long id) {
        // Chef can view any order for history purposes
        // Items will be filtered in the VIEW layer, not here
        return adminOrderService.findById(id);
    }

    @Override
    public Optional<Order> findByIdWithDetails(Long id) {
        // Chef can view any order details for history purposes
        // Items will be filtered in the VIEW layer, not here
        return adminOrderService.findByIdWithDetails(id);
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return adminOrderService.findByOrderNumber(orderNumber)
            .filter(order -> 
                order.getStatus() == OrderStatus.PENDING ||
                order.getStatus() == OrderStatus.IN_PREPARATION ||
                order.getStatus() == OrderStatus.READY
            );
    }

    @Override
    public List<Order> findByTableId(Long tableId) {
        // Chef can view all orders by table
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByTableId(tableId).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        // Chef can view active order by table
        // Items will be filtered in the VIEW layer
        return adminOrderService.findActiveOrderByTableId(tableId)
            .filter(this::hasItemsRequiringPreparation);
    }

    @Override
    public List<Order> findByEmployeeId(Long employeeId) {
        // Chef can view all orders by employee
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByEmployeeId(employeeId).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        // Chef can view orders in any status
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByStatus(status).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByOrderType(OrderType orderType) {
        // Chef can view all orders by type
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByOrderType(orderType).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findTodaysOrders() {
        // Chef can view all today's orders
        // Items will be filtered in the VIEW layer
        return adminOrderService.findTodaysOrders().stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findActiveOrders() {
        // Chef can view all active orders
        // Items will be filtered in the VIEW layer
        return adminOrderService.findActiveOrders().stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        // Chef can view all orders in date range
        // Items will be filtered in the VIEW layer
        return adminOrderService.findByDateRange(startDate, endDate).stream()
            .filter(this::hasItemsRequiringPreparation)
            .collect(Collectors.toList());
    }

    // ========== Validation Operations (delegate to admin) ==========

    @Override
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        return adminOrderService.validateStock(orderDetails);
    }

    @Override
    public boolean hasActiveOrder(Long tableId) {
        return adminOrderService.hasActiveOrder(tableId);
    }

    @Override
    public boolean isTableAvailableForOrder(Long tableId) {
        return adminOrderService.isTableAvailableForOrder(tableId);
    }

    // ========== Order Number Generation (delegate) ==========

    @Override
    public String generateOrderNumber() {
        return adminOrderService.generateOrderNumber();
    }

    // ========== Statistics (only for relevant orders) ==========

    @Override
    public long countByStatus(OrderStatus status) {
        if (status != OrderStatus.PENDING && 
            status != OrderStatus.IN_PREPARATION && 
            status != OrderStatus.READY) {
            return 0;
        }
        return adminOrderService.countByStatus(status);
    }

    @Override
    public long countTodaysOrders() {
        return findTodaysOrders().size();
    }

    @Override
    public long countTodaysOrdersByStatus(OrderStatus status) {
        if (status != OrderStatus.PENDING && 
            status != OrderStatus.IN_PREPARATION && 
            status != OrderStatus.READY) {
            return 0;
        }
        return findTodaysOrders().stream()
            .filter(order -> order.getStatus() == status)
            .count();
    }

    @Override
    public BigDecimal getTodaysRevenue() {
        // Chef doesn't need to see revenue
        return BigDecimal.ZERO;
    }

    @Override
    public Order findByIdOrThrow(Long id) {
        return findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado o no accesible"));
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        throw new UnsupportedOperationException("El chef no puede eliminar items de pedidos");
    }
}
