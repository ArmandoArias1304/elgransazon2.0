package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
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
 * DeliveryOrderServiceImpl - Implementation for DELIVERY role
 * 
 * Restrictions:
 * - Can only see DELIVERY orders (OrderType.DELIVERY)
 * - Can see READY orders (available to accept)
 * - Can see orders they have accepted (ON_THE_WAY)
 * - Can see orders they have delivered (DELIVERED, PAID)
 * - Can change status from READY to ON_THE_WAY (accepting delivery)
 * - Can change status from ON_THE_WAY to DELIVERED (completing delivery)
 * - Can change status from DELIVERED to PAID (confirming payment)
 * - Cannot cancel orders
 * - Cannot create new orders
 * - Cannot modify order details
 */
@Service("deliveryOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeliveryOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService; // Delegate to admin service for actual operations
    private final EmployeeRepository employeeRepository;

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Get current employee
     */
    private Employee getCurrentEmployee() {
        String username = getCurrentUsername();
        if (username == null) {
            throw new IllegalStateException("Usuario no autenticado");
        }
        return employeeRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado: " + username));
    }

    /**
     * Validate if delivery person can change to this status
     * DELIVERY can only change READY -> ON_THE_WAY, ON_THE_WAY -> DELIVERED, DELIVERED -> PAID
     */
    private void validateStatusChange(Order order, OrderStatus newStatus) {
        // Validate order type
        if (order.getOrderType() != OrderType.DELIVERY) {
            throw new IllegalStateException("Solo se pueden gestionar pedidos de tipo DELIVERY");
        }

        if (order.getStatus() == OrderStatus.READY && newStatus == OrderStatus.ON_THE_WAY) {
            return; // OK
        }
        if (order.getStatus() == OrderStatus.ON_THE_WAY && newStatus == OrderStatus.DELIVERED) {
            return; // OK
        }
        if (order.getStatus() == OrderStatus.DELIVERED && newStatus == OrderStatus.PAID) {
            return; // OK
        }
        
        throw new IllegalStateException(
            "El delivery solo puede cambiar el estado de LISTO a EN CAMINO, de EN CAMINO a ENTREGADO, o de ENTREGADO a PAGADO"
        );
    }

    // ========== CRUD Operations (restricted for delivery) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El delivery no puede crear pedidos");
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El delivery no puede modificar pedidos");
    }

    @Override
    public Order cancel(Long id, String username) {
        throw new UnsupportedOperationException("El delivery no puede cancelar pedidos");
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        Order order = findByIdOrThrow(id);
        Employee currentEmployee = getCurrentEmployee();
        
        // Validate that delivery person can only change status of orders they accepted
        // Exception: READY orders can be accepted by any delivery person
        if (order.getStatus() == OrderStatus.ON_THE_WAY || order.getStatus() == OrderStatus.DELIVERED) {
            if (order.getDeliveredBy() == null || !order.getDeliveredBy().getIdEmpleado().equals(currentEmployee.getIdEmpleado())) {
                throw new IllegalStateException("Solo puedes cambiar el estado de pedidos que tú aceptaste");
            }
        }
        
        validateStatusChange(order, newStatus);
        
        // Set deliveredBy when accepting the order (READY -> ON_THE_WAY)
        if (order.getStatus() == OrderStatus.READY && newStatus == OrderStatus.ON_THE_WAY) {
            order.setDeliveredBy(currentEmployee);
            log.info("Delivery person {} accepted order {}", currentEmployee.getFullName(), order.getOrderNumber());
        }
        
        return adminOrderService.changeStatus(id, newStatus, username);
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("El delivery no puede eliminar pedidos");
    }

    // ========== Query Operations (filtered for delivery - only DELIVERY orders) ==========

    @Override
    public List<Order> findAll() {
        return adminOrderService.findAll().stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findById(Long id) {
        Optional<Order> order = adminOrderService.findById(id);
        // Filter: only DELIVERY orders
        return order.filter(o -> o.getOrderType() == OrderType.DELIVERY);
    }

    @Override
    public Optional<Order> findByIdWithDetails(Long id) {
        Optional<Order> order = adminOrderService.findByIdWithDetails(id);
        // Filter: only DELIVERY orders
        return order.filter(o -> o.getOrderType() == OrderType.DELIVERY);
    }

    @Override
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return adminOrderService.findByOrderNumber(orderNumber)
                .filter(o -> o.getOrderType() == OrderType.DELIVERY);
    }

    @Override
    public List<Order> findByTableId(Long tableId) {
        // DELIVERY orders don't have tables
        throw new UnsupportedOperationException("Los pedidos de delivery no tienen mesa");
    }

    @Override
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        // DELIVERY orders don't have tables
        throw new UnsupportedOperationException("Los pedidos de delivery no tienen mesa");
    }

    @Override
    public List<Order> findByEmployeeId(Long employeeId) {
        return adminOrderService.findByEmployeeId(employeeId).stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return adminOrderService.findByStatus(status).stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByOrderType(OrderType orderType) {
        // Only DELIVERY orders
        if (orderType != OrderType.DELIVERY) {
            return List.of();
        }
        return adminOrderService.findByOrderType(OrderType.DELIVERY);
    }

    @Override
    public List<Order> findTodaysOrders() {
        return adminOrderService.findTodaysOrders().stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findActiveOrders() {
        return adminOrderService.findActiveOrders().stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return adminOrderService.findByDateRange(startDate, endDate).stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .collect(Collectors.toList());
    }

    // ========== Validation Operations (not applicable) ==========

    @Override
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("El delivery no puede validar stock");
    }

    @Override
    public boolean hasActiveOrder(Long tableId) {
        throw new UnsupportedOperationException("Los pedidos de delivery no tienen mesa");
    }

    @Override
    public boolean isTableAvailableForOrder(Long tableId) {
        throw new UnsupportedOperationException("Los pedidos de delivery no tienen mesa");
    }

    // ========== Order Number Generation (not applicable) ==========

    @Override
    public String generateOrderNumber() {
        throw new UnsupportedOperationException("El delivery no puede generar números de pedido");
    }

    // ========== Statistics (only for DELIVERY orders) ==========

    @Override
    public long countByStatus(OrderStatus status) {
        return adminOrderService.findByStatus(status).stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .count();
    }

    @Override
    public long countTodaysOrders() {
        return adminOrderService.findTodaysOrders().stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .count();
    }

    @Override
    public long countTodaysOrdersByStatus(OrderStatus status) {
        return adminOrderService.findTodaysOrders().stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .filter(order -> order.getStatus() == status)
                .count();
    }

    @Override
    public BigDecimal getTodaysRevenue() {
        return adminOrderService.findTodaysOrders().stream()
                .filter(order -> order.getOrderType() == OrderType.DELIVERY)
                .filter(order -> order.getStatus() == OrderStatus.PAID)
                .map(Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Order findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido de delivery no encontrado con ID: " + id));
    }

    // ========== New Item Management (not supported for delivery) ==========

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        throw new UnsupportedOperationException("El delivery no puede agregar items a pedidos");
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        throw new UnsupportedOperationException("El delivery no puede cambiar el estado de items individuales");
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        throw new UnsupportedOperationException("El delivery no puede eliminar items de pedidos");
    }
}
