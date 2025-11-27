package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
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
 * CustomerOrderServiceImpl - Implementation for Customer role
 * 
 * Restrictions:
 * - Can only create TAKEOUT and DELIVERY orders (no DINE_IN)
 * - Can only see their own orders
 * - Cannot edit, cancel, or change status of orders
 * - Read-only access to their order history
 */
@Service("customerOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomerOrderServiceImpl implements OrderService {

    private final OrderServiceImpl adminOrderService; // Delegate to admin service for actual operations
    private final com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository;
    private final com.aatechsolutions.elgransazon.domain.repository.CustomerRepository customerRepository;

    /**
     * Get current authenticated customer email
     */
    private String getCurrentCustomerEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Validate if customer can access this order
     */
    private void validateOrderOwnership(Order order) {
        String currentEmail = getCurrentCustomerEmail();
        
        // Check if order belongs to current customer
        if (currentEmail == null || order.getCustomer() == null || 
            !order.getCustomer().getEmail().equalsIgnoreCase(currentEmail)) {
            throw new IllegalStateException("No tiene permisos para acceder a este pedido");
        }
    }

    // ========== CRUD Operations (with restrictions) ==========

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        log.info("Customer {} creating new order", getCurrentCustomerEmail());
        
        // Validate order type: customers can only create TAKEOUT and DELIVERY orders
        if (order.getOrderType() == OrderType.DINE_IN) {
            throw new IllegalStateException("Los clientes no pueden crear pedidos para comer aquí (DINE_IN). Solo TAKEOUT o DELIVERY.");
        }
        
        // Ensure table and employee are null (customers don't use tables or employees)
        order.setTable(null);
        order.setEmployee(null);
        
        // Get customer and set it in the order
        String customerEmail = getCurrentCustomerEmail();
        if (customerEmail == null) {
            throw new IllegalStateException("Debe iniciar sesión para crear un pedido");
        }
        
        Customer customer = customerRepository.findByEmailIgnoreCase(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
        
        order.setCustomer(customer);
        
        // Set customer info from Customer entity
        order.setCustomerName(customer.getFullName());
        order.setCustomerPhone(customer.getPhone());
        
        // For DELIVERY orders, use customer's address if not provided
        if (order.getOrderType() == OrderType.DELIVERY) {
            if (order.getDeliveryAddress() == null || order.getDeliveryAddress().trim().isEmpty()) {
                if (customer.getAddress() == null || customer.getAddress().trim().isEmpty()) {
                    throw new IllegalStateException("Debe proporcionar una dirección de entrega o actualizar su dirección en su perfil");
                }
                order.setDeliveryAddress(customer.getAddress());
            }
        }
        
        // Set createdBy to customer email
        order.setCreatedBy(customerEmail);
        
        // Delegate to admin service
        return adminOrderService.create(order, orderDetails);
    }

    @Override
    public Order update(Long id, Order order, List<OrderDetail> orderDetails) {
        throw new UnsupportedOperationException("Los clientes no pueden editar pedidos");
    }

    @Override
    public Order cancel(Long id, String username) {
        log.info("Customer {} cancelling order {}", getCurrentCustomerEmail(), id);
        
        // Get the order and validate ownership
        Order order = findByIdOrThrow(id);
        validateOrderOwnership(order);
        
        // Validate order is in PENDING status (customers can only cancel PENDING orders)
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                "Solo se pueden cancelar pedidos en estado PENDIENTE. Estado actual: " + order.getStatus().getDisplayName()
            );
        }
        
        // Delegate to admin service (stock will be returned automatically for PENDING orders)
        return adminOrderService.cancel(id, username);
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String username) {
        throw new UnsupportedOperationException("Los clientes no pueden cambiar el estado de los pedidos");
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        log.info("Customer {} adding items to order {}", getCurrentCustomerEmail(), orderId);
        
        // Get the order and validate ownership
        Order order = findByIdOrThrow(orderId);
        validateOrderOwnership(order);
        
        // Validate order can accept new items (uses canAcceptNewItems() which checks order type and status)
        if (!order.canAcceptNewItems()) {
            throw new IllegalStateException(
                String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s",
                    order.getOrderType().getDisplayName(),
                    order.getStatus().getDisplayName())
            );
        }
        
        // Delegate to admin service
        return adminOrderService.addItemsToExistingOrder(orderId, newItems, username);
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        throw new UnsupportedOperationException("Los clientes no pueden cambiar el estado de los items");
    }

    @Override
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        throw new UnsupportedOperationException("Los clientes no pueden eliminar items de pedidos");
    }

    @Override
    public void delete(Long id) {
        throw new UnsupportedOperationException("Los clientes no pueden eliminar pedidos");
    }

    // ========== Query Operations (filtered by customer) ==========

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        String currentEmail = getCurrentCustomerEmail();
        log.debug("Customer {} fetching their orders", currentEmail);
        
        // Return orders by customer email
        return orderRepository.findByCustomerEmail(currentEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        Optional<Order> order = adminOrderService.findById(id);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                log.warn("Customer {} tried to access order {} they don't own", getCurrentCustomerEmail(), id);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdWithDetails(Long id) {
        Optional<Order> order = adminOrderService.findByIdWithDetails(id);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                log.warn("Customer {} tried to access order {} they don't own", getCurrentCustomerEmail(), id);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(String orderNumber) {
        Optional<Order> order = adminOrderService.findByOrderNumber(orderNumber);
        if (order.isPresent()) {
            try {
                validateOrderOwnership(order.get());
                return order;
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByTableId(Long tableId) {
        // Customers don't use tables
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        // Customers don't use tables
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByEmployeeId(Long employeeId) {
        // Customers don't query by employee
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        String currentEmail = getCurrentCustomerEmail();
        return findAll().stream()
                .filter(order -> order.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByOrderType(OrderType orderType) {
        String currentEmail = getCurrentCustomerEmail();
        return findAll().stream()
                .filter(order -> order.getOrderType() == orderType)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findTodaysOrders() {
        return findAll().stream()
                .filter(order -> order.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findActiveOrders() {
        return findAll().stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED && 
                               order.getStatus() != OrderStatus.DELIVERED && 
                               order.getStatus() != OrderStatus.PAID)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return findAll().stream()
                .filter(order -> !order.getCreatedAt().isBefore(startDate) && 
                               !order.getCreatedAt().isAfter(endDate))
                .collect(Collectors.toList());
    }

    // ========== Validation Operations (delegate to admin) ==========

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        return adminOrderService.validateStock(orderDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrder(Long tableId) {
        // Customers don't use tables
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTableAvailableForOrder(Long tableId) {
        // Customers don't use tables
        return false;
    }

    // ========== Order Number Generation (delegate) ==========

    @Override
    public String generateOrderNumber() {
        return adminOrderService.generateOrderNumber();
    }

    // ========== Statistics (only for customer's orders) ==========

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OrderStatus status) {
        return findByStatus(status).size();
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrders() {
        return findTodaysOrders().size();
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrdersByStatus(OrderStatus status) {
        return findTodaysOrders().stream()
                .filter(order -> order.getStatus() == status)
                .count();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTodaysRevenue() {
        // Customers can't see revenue
        return BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Order findByIdOrThrow(Long id) {
        Order order = adminOrderService.findByIdOrThrow(id);
        validateOrderOwnership(order);
        return order;
    }
}
