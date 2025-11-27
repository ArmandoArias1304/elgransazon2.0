package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * OrderServiceImpl - Business logic implementation for Orders management
 * This is the ADMIN implementation with full access
 * 
 * Handles:
 * - Order creation with stock validation
 * - Table state management
 * - Stock deduction and return
 * - Order status transitions
 * - Order cancellation
 * - Statistics and reports
 */
@Service("adminOrderService")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ItemMenuRepository itemMenuRepository;
    private final SystemConfigurationRepository systemConfigurationRepository;
    private final RestaurantTableService restaurantTableService;
    private final WebSocketNotificationService wsNotificationService;

    @Override
    public Order create(Order order, List<OrderDetail> orderDetails) {
        log.info("Creating new order - Type: {}, Table: {}", 
                 order.getOrderType(), 
                 order.getTable() != null ? order.getTable().getTableNumber() : "N/A");
        
        // 1. Validate table requirement based on order type
        validateTableRequirement(order);
        
        // 2. Validate table availability (only if table is provided)
        RestaurantTable table = order.getTable();
        if (table != null) {
            if (!isTableAvailableForOrder(table.getId())) {
                throw new IllegalStateException(
                    String.format("La mesa #%d no está disponible o ya tiene un pedido activo", 
                                  table.getTableNumber())
                );
            }
        }

        // 3. Validate customer information based on order type
        validateCustomerInformation(order);

        // 4. Validate payment method is enabled
        validatePaymentMethod(order.getPaymentMethod());

        // 5. Validate stock availability for all items
        Map<Long, String> stockErrors = validateStock(orderDetails);
        if (!stockErrors.isEmpty()) {
            throw new IllegalStateException(
                "Stock insuficiente para los siguientes items: " + 
                String.join(", ", stockErrors.values())
            );
        }

        // 6. Generate unique order number
        String orderNumber = generateOrderNumber();
        order.setOrderNumber(orderNumber);

        // 7. Get tax rate from system configuration
        BigDecimal taxRate = getTaxRate();
        order.setTaxRate(taxRate);

        // 8. Process order details and deduct stock
        for (OrderDetail detail : orderDetails) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            // Set current price if not set
            if (detail.getUnitPrice() == null) {
                detail.setUnitPrice(item.getPrice());
            }

            // Calculate subtotal
            detail.calculateSubtotal();

            // Initialize item status as PENDING by default
            detail.setItemStatus(OrderStatus.PENDING);
            detail.setIsNewItem(false); // Initial items are not "new"
            detail.setAddedAt(LocalDateTime.now());
            
            // Auto-advance to READY if item does NOT require preparation
            if (!Boolean.TRUE.equals(item.getRequiresPreparation())) {
                detail.setItemStatus(OrderStatus.READY);
                log.info("Item '{}' auto-advanced to READY (no preparation required)", item.getName());
            }

            // Deduct stock from ingredients
            deductStockForItem(item, detail.getQuantity());

            // Update item availability
            item.updateAvailability();
            itemMenuRepository.save(item);

            // Add to order
            order.addOrderDetail(detail);
        }

        // 9. Calculate order totals
        order.recalculateAmounts();

        // 10. Reserve/Occupy table if applicable (only for DINE_IN)
        if (table != null && order.getOrderType() == OrderType.DINE_IN) {
            if (table.getStatus() == TableStatus.AVAILABLE) {
                // Mesa AVAILABLE → cambiar a OCCUPIED (is_occupied se mantiene en false)
                table.setStatus(TableStatus.OCCUPIED);
                // NO cambiar is_occupied, se mantiene en false
                log.info("Table #{} changed from AVAILABLE to OCCUPIED (is_occupied=false)", 
                         table.getTableNumber());
            } else if (table.getStatus() == TableStatus.RESERVED) {
                // Mesa RESERVED → mantener status RESERVED, solo cambiar is_occupied a true
                // Validar tiempo de consumo antes de ocupar
                try {
                    RestaurantTable markedTable = restaurantTableService.markAsOccupied(
                        table.getId(), 
                        order.getCreatedBy()
                    );
                    table.setIsOccupied(markedTable.getIsOccupied());
                    log.info("Reserved table #{} marked as occupied (is_occupied=true)", 
                             table.getTableNumber());
                } catch (IllegalStateException e) {
                    log.error("Cannot occupy reserved table: {}", e.getMessage());
                    throw new IllegalArgumentException(
                        "No se puede ocupar la mesa " + table.getTableNumber() + ": " + e.getMessage()
                    );
                }
            }
            restaurantTableRepository.save(table);
            log.info("Table #{} marked as occupied (Status: {})", 
                     table.getTableNumber(), table.getStatus());
        }

        // 11. Save order
        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully: {} (Type: {}) with total: ${}", 
                 savedOrder.getOrderNumber(), 
                 savedOrder.getOrderType().getDisplayName(),
                 savedOrder.getTotal());

        // 12. Send WebSocket notification for new order
        try {
            wsNotificationService.notifyNewOrder(savedOrder);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for new order: {}", savedOrder.getOrderNumber(), e);
        }

        // 13. Auto-advance to READY if ALL items don't require preparation
        autoAdvanceOrderIfNoPreparationNeeded(savedOrder);

        return savedOrder;
    }

    @Override
    public Order update(Long id, Order updatedOrder, List<OrderDetail> newOrderDetails) {
        log.info("Updating order with ID: {}", id);

        Order existingOrder = findByIdOrThrow(id);

        // Cannot update PAID or CANCELLED orders
        if (existingOrder.getStatus() == OrderStatus.PAID || existingOrder.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                "No se pueden modificar pedidos PAGADOS o CANCELADOS. Estado actual: " + 
                existingOrder.getStatus().getDisplayName()
            );
        }

        // Store old table reference before updating
        RestaurantTable oldTable = existingOrder.getTable();
        RestaurantTable newTable = updatedOrder.getTable();
        OrderType oldOrderType = existingOrder.getOrderType();
        OrderType newOrderType = updatedOrder.getOrderType();

        // Validate table requirement for the new order type
        validateTableRequirement(updatedOrder);

        // Validate new table availability if table is changing
        if (newTable != null) {
            // If table is different from current, validate new table availability
            if (oldTable == null || !oldTable.getId().equals(newTable.getId())) {
                if (!isTableAvailableForOrder(newTable.getId())) {
                    throw new IllegalStateException(
                        String.format("La mesa #%d no está disponible o ya tiene un pedido activo", 
                                      newTable.getTableNumber())
                    );
                }
            }
        }

        // Validate customer information based on order type
        validateCustomerInformation(updatedOrder);

        // Return stock for old items
        returnStockForOrder(existingOrder);

        // Validate stock for new items
        Map<Long, String> stockErrors = validateStock(newOrderDetails);
        if (!stockErrors.isEmpty()) {
            throw new IllegalStateException(
                "Stock insuficiente para los siguientes items: " + 
                String.join(", ", stockErrors.values())
            );
        }

        // Clear existing details
        orderDetailRepository.deleteByOrder(existingOrder);
        existingOrder.getOrderDetails().clear();

        // Add new details and deduct stock
        for (OrderDetail newDetail : newOrderDetails) {
            ItemMenu item = itemMenuRepository.findById(newDetail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + newDetail.getItemMenu().getIdItemMenu()
                ));

            // Set current price
            newDetail.setUnitPrice(item.getPrice());
            newDetail.calculateSubtotal();

            // Deduct stock
            deductStockForItem(item, newDetail.getQuantity());

            // Update item availability
            item.updateAvailability();
            itemMenuRepository.save(item);

            // Add to order
            existingOrder.addOrderDetail(newDetail);
        }

        // Update basic fields
        existingOrder.setOrderType(newOrderType);
        existingOrder.setCustomerName(updatedOrder.getCustomerName());
        existingOrder.setCustomerPhone(updatedOrder.getCustomerPhone());
        existingOrder.setDeliveryAddress(updatedOrder.getDeliveryAddress());
        existingOrder.setDeliveryReferences(updatedOrder.getDeliveryReferences());
        existingOrder.setPaymentMethod(updatedOrder.getPaymentMethod());

        // Handle table changes
        handleTableChange(existingOrder, oldTable, newTable, oldOrderType, newOrderType, 
                         updatedOrder.getUpdatedBy());

        // Recalculate totals
        existingOrder.recalculateAmounts();

        // Save updated order
        Order savedOrder = orderRepository.save(existingOrder);
        log.info("Order updated successfully: {}", savedOrder.getOrderNumber());

        return savedOrder;
    }

    @Override
    public Order cancel(Long id, String cancelledBy) {
        log.info("Cancelling order with ID: {}", id);

        Order order = findByIdOrThrow(id);

        // Check if order can be cancelled
        if (!order.getStatus().canBeCancelled()) {
            throw new IllegalStateException(
                String.format("No se puede cancelar un pedido con estado: %s. " +
                             "Solo se pueden cancelar pedidos en estados: PENDING, IN_PREPARATION, READY",
                              order.getStatus().getDisplayName())
            );
        }

        // IMPORTANT: Check if any items have been DELIVERED
        // Even if the order status is PENDING (due to new items added), 
        // we cannot cancel if some items were already delivered
        boolean hasDeliveredItems = order.getOrderDetails().stream()
            .anyMatch(detail -> detail.getItemStatus() == OrderStatus.DELIVERED);
        
        if (hasDeliveredItems) {
            throw new IllegalStateException(
                "No se puede cancelar este pedido porque ya tiene items que fueron entregados. " +
                "Si desea cancelar los items pendientes, debe hacerlo individualmente."
            );
        }

        OrderStatus currentStatus = order.getStatus();
        
        // Analyze items individually to determine stock return strategy
        List<OrderDetail> itemsToReturnAutomatically = new ArrayList<>();
        List<OrderDetail> itemsToReturnManually = new ArrayList<>();
        
        for (OrderDetail detail : order.getOrderDetails()) {
            if (shouldReturnStockAutomatically(detail)) {
                itemsToReturnAutomatically.add(detail);
            } else {
                itemsToReturnManually.add(detail);
            }
        }
        
        // Return stock automatically for eligible items
        if (!itemsToReturnAutomatically.isEmpty()) {
            for (OrderDetail detail : itemsToReturnAutomatically) {
                ItemMenu item = detail.getItemMenu();
                returnStockForItem(item, detail.getQuantity());
            }
            log.info("Stock returned automatically for {} items in order: {}", 
                     itemsToReturnAutomatically.size(), order.getOrderNumber());
        }
        
        // Log items that need manual stock return
        if (!itemsToReturnManually.isEmpty()) {
            String manualItems = itemsToReturnManually.stream()
                .map(d -> d.getItemMenu().getName())
                .collect(java.util.stream.Collectors.joining(", "));
            log.warn("Order {} has {} items that need MANUAL stock return: {}", 
                     order.getOrderNumber(), itemsToReturnManually.size(), manualItems);
        }

        // Update order status to CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedBy(cancelledBy);
        order.setUpdatedAt(java.time.LocalDateTime.now());

        // Free table if applicable (DINE_IN only)
        if (order.getOrderType() == OrderType.DINE_IN) {
            RestaurantTable table = order.getTable();
            if (table != null) {
                // If table is RESERVED, only change is_occupied to false
                // If table is OCCUPIED, change status to AVAILABLE
                if (table.getStatus() == TableStatus.RESERVED) {
                    table.setIsOccupied(false);
                    log.info("Reserved table #{} is_occupied set to false after order cancellation", 
                             table.getTableNumber());
                } else if (table.getStatus() == TableStatus.OCCUPIED) {
                    table.setStatus(TableStatus.AVAILABLE);
                    table.setIsOccupied(false);
                    log.info("Table #{} freed and marked as AVAILABLE after order cancellation", 
                             table.getTableNumber());
                }
                table.setUpdatedBy(cancelledBy);
                restaurantTableRepository.save(table);
            }
        }

        Order cancelledOrder = orderRepository.save(order);
        log.info("Order cancelled successfully: {} (was in {} status)", 
                 cancelledOrder.getOrderNumber(), currentStatus.getDisplayName());
        
        // Send WebSocket notification for order cancellation
        try {
            String cancelMessage = String.format("Pedido cancelado: %s (anterior: %s)", 
                cancelledOrder.getOrderNumber(), currentStatus.getDisplayName());
            wsNotificationService.notifyOrderStatusChange(cancelledOrder, cancelMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for order cancellation: {}", 
                cancelledOrder.getOrderNumber(), e);
        }
        
        return cancelledOrder;
    }

    @Override
    public Order changeStatus(Long id, OrderStatus newStatus, String updatedBy) {
        log.info("Changing order status. ID: {}, New Status: {}", id, newStatus);

        Order order = findByIdOrThrow(id);
        OrderStatus oldStatus = order.getStatus();
        OrderType orderType = order.getOrderType();

        // Validate status transition
        if (oldStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cambiar el estado de un pedido cancelado");
        }

        if (oldStatus == OrderStatus.PAID) {
            throw new IllegalStateException("No se puede cambiar el estado de un pedido ya pagado");
        }

        // Validate ON_THE_WAY is only for DELIVERY orders
        if (newStatus == OrderStatus.ON_THE_WAY && orderType != OrderType.DELIVERY) {
            throw new IllegalStateException(
                "El estado 'EN CAMINO' solo es válido para pedidos de tipo ENTREGA A DOMICILIO"
            );
        }

        // Validate transition is allowed
        if (!OrderStatus.isValidTransition(oldStatus, newStatus, orderType)) {
            throw new IllegalStateException(
                String.format("Transición de estado inválida: %s -> %s para pedido tipo %s",
                    oldStatus.getDisplayName(),
                    newStatus.getDisplayName(),
                    orderType.getDisplayName())
            );
        }

        // Update status
        order.setStatus(newStatus);
        order.setUpdatedBy(updatedBy);
        order.setUpdatedAt(LocalDateTime.now()); // Explicitly set updatedAt

        // Update item statuses to match order status (IMPORTANT: only for non-new items or when applicable)
        // When order changes to IN_PREPARATION, READY, or DELIVERED, update all items that aren't individually managed
        if (newStatus == OrderStatus.IN_PREPARATION || 
            newStatus == OrderStatus.READY || 
            newStatus == OrderStatus.DELIVERED) {
            
            for (OrderDetail detail : order.getOrderDetails()) {
                // Only update items that are not being individually tracked by chef
                // Or update all items if order is being bulk-changed
                if (detail.getItemStatus() == null || 
                    detail.getItemStatus() == OrderStatus.PENDING ||
                    detail.getItemStatus().ordinal() < newStatus.ordinal()) {
                    detail.setItemStatus(newStatus);
                }
            }
            log.info("Updated item statuses to {} for order {}", newStatus, order.getOrderNumber());
        }

        // NOTE: preparedBy and paidBy should be set in the controller BEFORE calling this method
        // This is just a fallback in case they weren't set
        
        // Track who prepared the order (when status changes to READY) - Fallback only
        if (newStatus == OrderStatus.READY && order.getPreparedBy() == null) {
            // This shouldn't happen if controller set it properly when changing to IN_PREPARATION
            log.warn("Order {} marked as READY but preparedBy is null - using fallback", order.getOrderNumber());
            order.setPreparedBy(order.getEmployee()); // Fallback to order creator
        }

        // Track who collected payment (when status changes to PAID) - Fallback only
        if (newStatus == OrderStatus.PAID && order.getPaidBy() == null) {
            // This shouldn't happen if controller set it properly
            log.warn("Order {} marked as PAID but paidBy is null - using fallback", order.getOrderNumber());
            order.setPaidBy(order.getEmployee()); // Fallback to order creator - FIXED: was setPreparedBy
        }

        // If order is marked as PAID, free the table
        // NOTE: Table is NOT freed when DELIVERED - only when PAID
        if (newStatus == OrderStatus.PAID && orderType == OrderType.DINE_IN) {
            RestaurantTable table = order.getTable();
            if (table != null) {
                // If table is RESERVED, only change is_occupied
                // If table is OCCUPIED, change to AVAILABLE
                if (table.getStatus() == TableStatus.RESERVED) {
                    table.setIsOccupied(false);
                    log.info("Reserved table #{} is_occupied set to false after order payment", 
                             table.getTableNumber());
                } else if (table.getStatus() == TableStatus.OCCUPIED) {
                    table.setStatus(TableStatus.AVAILABLE);
                    table.setIsOccupied(false);
                    log.info("Table #{} freed and marked as AVAILABLE after order payment", 
                             table.getTableNumber());
                }
                table.setUpdatedBy(updatedBy);
                restaurantTableRepository.save(table);
            }
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order status changed: {} -> {}", oldStatus, newStatus);

        // Send WebSocket notification for status change
        try {
            String statusMessage = String.format("Estado cambiado: %s → %s", 
                oldStatus.getDisplayName(), newStatus.getDisplayName());
            wsNotificationService.notifyOrderStatusChange(savedOrder, statusMessage);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for status change: {}", savedOrder.getOrderNumber(), e);
        }

        return savedOrder;
    }

    @Override
    public Order addItemsToExistingOrder(Long orderId, List<OrderDetail> newItems, String username) {
        log.info("Adding {} new items to order ID: {}", newItems.size(), orderId);

        Order order = findByIdOrThrow(orderId);

        // Validate that order can accept new items
        if (!order.canAcceptNewItems()) {
            throw new IllegalStateException(
                String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s. " +
                             "Solo pedidos PARA COMER AQUÍ, PARA LLEVAR y ENTREGA A DOMICILIO pueden recibir items adicionales.",
                              order.getOrderType().getDisplayName(),
                              order.getStatus().getDisplayName())
            );
        }

        // Validate stock for new items
        Map<Long, String> stockErrors = validateStock(newItems);
        if (!stockErrors.isEmpty()) {
            throw new IllegalStateException(
                "Stock insuficiente para los siguientes items: " + 
                String.join(", ", stockErrors.values())
            );
        }

        // Process each new item
        for (OrderDetail detail : newItems) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            // Set current price if not set
            if (detail.getUnitPrice() == null) {
                detail.setUnitPrice(item.getPrice());
            }

            // Calculate subtotal
            detail.calculateSubtotal();

            // Mark as new item
            detail.markAsNew();
            
            // Initialize as PENDING by default
            detail.setItemStatus(OrderStatus.PENDING);
            
            // Auto-advance to READY if item does NOT require preparation
            if (!Boolean.TRUE.equals(item.getRequiresPreparation())) {
                detail.setItemStatus(OrderStatus.READY);
                log.info("New item '{}' auto-advanced to READY (no preparation required)", item.getName());
            }

            // Deduct stock from ingredients
            deductStockForItem(item, detail.getQuantity());

            // Add to order
            detail.setOrder(order);
            order.addOrderDetail(detail);
        }

        // Recalculate order totals
        order.recalculateAmounts();

        // Update order status based on items
        order.updateStatusFromItems();

        // Set audit fields
        order.setUpdatedBy(username);
        order.setUpdatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);
        log.info("Added {} new items to order {}. New total: {}", 
                 newItems.size(), 
                 savedOrder.getOrderNumber(),
                 savedOrder.getFormattedTotal());

        // Send WebSocket notification for items added to existing order
        try {
            wsNotificationService.notifyItemsAdded(savedOrder, newItems.size());
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for items added to order: {}", 
                savedOrder.getOrderNumber(), e);
        }

        return savedOrder;
    }

    @Override
    public Order changeItemsStatus(Long orderId, List<Long> itemDetailIds, OrderStatus newStatus, String username) {
        log.info("Changing status of {} items in order ID: {} to {}", 
                 itemDetailIds.size(), orderId, newStatus);

        Order order = findByIdOrThrow(orderId);

        // Find and update each item
        for (Long itemDetailId : itemDetailIds) {
            OrderDetail detail = order.getOrderDetails().stream()
                .filter(d -> d.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item detail no encontrado en esta orden: " + itemDetailId
                ));

            // Validate status transition for this item
            OrderStatus oldItemStatus = detail.getItemStatus();
            
            // Basic validation: can't go backwards
            if (oldItemStatus == OrderStatus.DELIVERED) {
                throw new IllegalStateException(
                    "No se puede cambiar el estado de un item ya entregado: " + detail.getItemMenu().getName()
                );
            }

            // Set new status
            detail.setItemStatus(newStatus);

            // Track who prepared the item when it becomes IN_PREPARATION
            if (newStatus == OrderStatus.IN_PREPARATION && detail.getPreparedBy() == null) {
                detail.setPreparedBy(username);
            }

            log.info("Item '{}' status changed: {} -> {}", 
                     detail.getItemMenu().getName(), oldItemStatus, newStatus);
        }

        // Update order's overall status based on all items
        order.updateStatusFromItems();

        // Set audit fields
        order.setUpdatedBy(username);
        order.setUpdatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);
        log.info("Order {} status recalculated to: {}", 
                 savedOrder.getOrderNumber(), 
                 savedOrder.getStatus());

        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdWithDetails(Long id) {
        return orderRepository.findByIdWithDetails(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Order findByIdOrThrow(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByTableId(Long tableId) {
        return orderRepository.findByTableId(tableId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findActiveOrderByTableId(Long tableId) {
        return orderRepository.findActiveOrderByTableId(tableId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByEmployeeId(Long employeeId) {
        return orderRepository.findByEmployeeId(employeeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByOrderType(OrderType orderType) {
        return orderRepository.findByOrderType(orderType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findTodaysOrders() {
        return orderRepository.findTodaysOrders();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findActiveOrders() {
        return orderRepository.findActiveOrders();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return orderRepository.findByDateRange(startDate, endDate);
    }

    @Override
    public void delete(Long id) {
        log.info("Deleting order with ID: {}", id);
        
        Order order = findByIdOrThrow(id);
        
        // Only allow deletion if order is CANCELLED
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                "Solo se pueden eliminar pedidos cancelados. Estado actual: " + 
                order.getStatus().getDisplayName()
            );
        }

        orderRepository.deleteById(id);
        log.info("Order deleted successfully: {}", order.getOrderNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> validateStock(List<OrderDetail> orderDetails) {
        Map<Long, String> errors = new HashMap<>();

        for (OrderDetail detail : orderDetails) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            if (!item.hasEnoughStock(detail.getQuantity())) {
                errors.put(item.getIdItemMenu(), item.getName());
            }
        }

        return errors;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveOrder(Long tableId) {
        return orderRepository.findActiveOrderByTableId(tableId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTableAvailableForOrder(Long tableId) {
        RestaurantTable table = restaurantTableRepository.findById(tableId)
            .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + tableId));

        // Table must be AVAILABLE or (RESERVED but not occupied)
        boolean statusOk = table.getStatus() == TableStatus.AVAILABLE ||
                          (table.getStatus() == TableStatus.RESERVED && !table.getIsOccupied());

        // Check if there's already an active order for this table
        boolean noActiveOrder = !hasActiveOrder(tableId);

        return statusOk && noActiveOrder;
    }

    @Override
    public synchronized String generateOrderNumber() {
        LocalDate today = LocalDate.now();
        String datePrefix = String.format("ORD-%04d%02d%02d-", 
                                         today.getYear(), 
                                         today.getMonthValue(), 
                                         today.getDayOfMonth());

        // Get the last order number for today and extract the sequence
        Optional<String> lastOrderNumber = orderRepository.findLastOrderNumberToday();
        
        int sequence = 1; // Default to 1 for the first order of the day
        
        if (lastOrderNumber.isPresent()) {
            String lastNumber = lastOrderNumber.get();
            try {
                // Extract the sequence number from the last order number
                // Format: ORD-YYYYMMDD-XXX
                String sequencePart = lastNumber.substring(lastNumber.lastIndexOf('-') + 1);
                int lastSequence = Integer.parseInt(sequencePart);
                sequence = lastSequence + 1;
            } catch (Exception e) {
                log.warn("Error parsing last order number: {}. Using count fallback.", lastOrderNumber.get());
                // Fallback to count method
                Long todayCount = orderRepository.countOrdersCreatedToday();
                sequence = todayCount.intValue() + 1;
            }
        }

        String orderNumber = String.format("%s%03d", datePrefix, sequence);
        
        // Safety check: if the order number already exists, increment until we find a unique one
        int maxAttempts = 100;
        int attempts = 0;
        while (orderRepository.existsByOrderNumber(orderNumber) && attempts < maxAttempts) {
            sequence++;
            orderNumber = String.format("%s%03d", datePrefix, sequence);
            attempts++;
        }
        
        if (attempts >= maxAttempts) {
            throw new IllegalStateException("No se pudo generar un número de orden único después de " + maxAttempts + " intentos");
        }
        
        log.debug("Generated order number: {}", orderNumber);
        return orderNumber;
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OrderStatus status) {
        Long count = orderRepository.countByStatus(status);
        return count != null ? count : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrders() {
        // Count all PAID orders (regardless of date)
        Long count = orderRepository.countByStatus(OrderStatus.PAID);
        return count != null ? count : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public long countTodaysOrdersByStatus(OrderStatus status) {
        // Count today's orders filtered by status
        List<Order> todaysOrders = findTodaysOrders();
        return todaysOrders.stream()
            .filter(order -> order.getStatus() == status)
            .count();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTodaysRevenue() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23, 59, 59);

        return orderRepository.findByDateRange(startOfDay, endOfDay).stream()
                .filter(order -> order.getStatus() == OrderStatus.PAID)
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Deduct stock for a menu item
     */
    private void deductStockForItem(ItemMenu item, Integer quantity) {
        log.debug("Deducting stock for item: {} (quantity: {})", item.getName(), quantity);

        for (ItemIngredient itemIngredient : item.getIngredients()) {
            try {
                itemIngredient.deductFromStock(quantity);
                log.debug("Stock deducted for ingredient: {} ({})", 
                         itemIngredient.getIngredientName(),
                         itemIngredient.getFormattedQuantity());
            } catch (IllegalStateException e) {
                log.error("Error deducting stock: {}", e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Return stock for an order (when cancelling or updating)
     */
    private void returnStockForOrder(Order order) {
        log.info("Returning stock for order: {}", order.getOrderNumber());

        for (OrderDetail detail : order.getOrderDetails()) {
            ItemMenu item = itemMenuRepository.findById(detail.getItemMenu().getIdItemMenu())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Item de menú no encontrado: " + detail.getItemMenu().getIdItemMenu()
                ));

            returnStockForItem(item, detail.getQuantity());

            // Update item availability
            item.updateAvailability();
            itemMenuRepository.save(item);
        }
    }

    /**
     * Return stock for a menu item
     */
    private void returnStockForItem(ItemMenu item, Integer quantity) {
        log.debug("Returning stock for item: {} (quantity: {})", item.getName(), quantity);

        for (ItemIngredient itemIngredient : item.getIngredients()) {
            Ingredient ingredient = itemIngredient.getIngredient();
            BigDecimal quantityToReturn = itemIngredient.getQuantity()
                .multiply(BigDecimal.valueOf(quantity));

            BigDecimal currentStock = ingredient.getCurrentStock() != null 
                ? ingredient.getCurrentStock() 
                : BigDecimal.ZERO;

            BigDecimal newStock = currentStock.add(quantityToReturn);
            ingredient.setCurrentStock(newStock);

            log.debug("Stock returned for ingredient: {} ({} {})", 
                     ingredient.getName(),
                     quantityToReturn.stripTrailingZeros().toPlainString(),
                     itemIngredient.getUnit());
        }
    }

    /**
     * Get tax rate from system configuration
     */
    private BigDecimal getTaxRate() {
        SystemConfiguration config = systemConfigurationRepository.findAll()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No se encontró la configuración del sistema"
            ));

        return config.getTaxRate();
    }

    /**
     * Automatically advance order to READY status if ALL items don't require preparation
     * This allows orders with ONLY drinks/pre-packaged items to skip the chef
     * 
     * @param order The order to check and potentially auto-advance
     */
    private void autoAdvanceOrderIfNoPreparationNeeded(Order order) {
        // Only auto-advance if order is currently PENDING
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        // Check if ALL items don't require preparation
        boolean allItemsReady = order.getOrderDetails().stream()
            .allMatch(detail -> detail.getItemMenu() != null 
                && Boolean.FALSE.equals(detail.getItemMenu().getRequiresPreparation()));

        if (allItemsReady && !order.getOrderDetails().isEmpty()) {
            log.info("Order {} contains ONLY items that don't require preparation. Auto-advancing to READY status.", 
                     order.getOrderNumber());
            
            // Change order status to READY
            order.setStatus(OrderStatus.READY);
            
            // Update all item statuses to READY
            for (OrderDetail detail : order.getOrderDetails()) {
                detail.setItemStatus(OrderStatus.READY);
            }
            
            // Save changes
            orderRepository.save(order);
            
            log.info("Order {} auto-advanced to READY (items: {})", 
                     order.getOrderNumber(),
                     order.getOrderDetails().stream()
                         .map(d -> d.getItemMenu().getName())
                         .collect(java.util.stream.Collectors.joining(", ")));
        }
    }

    /**
     * Validate table requirement based on order type
     * DINE_IN: Table is required
     * TAKEOUT: Table is optional
     * DELIVERY: Table should not be assigned
     */
    private void validateTableRequirement(Order order) {
        OrderType orderType = order.getOrderType();
        RestaurantTable table = order.getTable();
        
        if (orderType == OrderType.DINE_IN) {
            // DINE_IN requires a table
            if (table == null) {
                throw new IllegalArgumentException("Se requiere asignar una mesa para pedidos 'Para comer aquí'");
            }
        } else if (orderType == OrderType.DELIVERY) {
            // DELIVERY should not have a table
            if (table != null) {
                log.warn("Table assigned to DELIVERY order will be ignored");
                order.setTable(null); // Remove table for delivery orders
            }
        } else if (orderType == OrderType.TAKEOUT) {
            // TAKEOUT is optional - can have table or not
            if (table != null) {
                log.info("Table #{} assigned to TAKEOUT order", table.getTableNumber());
            }
        }
    }

    /**
     * Handle table changes when updating an order
     * Frees old table and occupies new table as needed
     */
    private void handleTableChange(Order order, RestaurantTable oldTable, RestaurantTable newTable, 
                                   OrderType oldOrderType, OrderType newOrderType, String username) {
        
        log.info("Handling table change - Old: {}, New: {}, OldType: {}, NewType: {}", 
                 oldTable != null ? "Table #" + oldTable.getTableNumber() : "null",
                 newTable != null ? "Table #" + newTable.getTableNumber() : "null",
                 oldOrderType, newOrderType);
        
        boolean oldWasDineIn = oldOrderType == OrderType.DINE_IN;
        boolean newIsDineIn = newOrderType == OrderType.DINE_IN;
        
        // Case 1: Old order had a table and was DINE_IN -> free the old table
        if (oldTable != null && oldWasDineIn) {
            // Check if we're changing to a different table or changing order type
            if (newTable == null || !oldTable.getId().equals(newTable.getId()) || !newIsDineIn) {
                log.info("Freeing old table #{} (was {})", oldTable.getTableNumber(), oldTable.getStatus());
                
                // Refresh table from DB to get latest status
                RestaurantTable tableToFree = restaurantTableRepository.findById(oldTable.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + oldTable.getId()));
                
                // Si la mesa está RESERVED, solo cambiar is_occupied a false
                // Si la mesa está OCCUPIED, cambiar status a AVAILABLE (is_occupied ya está en false)
                if (tableToFree.getStatus() == TableStatus.RESERVED) {
                    tableToFree.setIsOccupied(false);
                    log.info("Reserved table #{} - Setting isOccupied to false (status remains RESERVED)", 
                             tableToFree.getTableNumber());
                } else if (tableToFree.getStatus() == TableStatus.OCCUPIED) {
                    tableToFree.setStatus(TableStatus.AVAILABLE);
                    // is_occupied ya está en false, no necesita cambiarse
                    log.info("Occupied table #{} - Setting status to AVAILABLE (isOccupied already false)", 
                             tableToFree.getTableNumber());
                }
                
                tableToFree.setUpdatedBy(username);
                RestaurantTable savedOldTable = restaurantTableRepository.save(tableToFree);
                restaurantTableRepository.flush(); // Force immediate persistence
                log.info("Table #{} freed successfully - Status: {}, isOccupied: {}", 
                         savedOldTable.getTableNumber(), 
                         savedOldTable.getStatus(), 
                         savedOldTable.getIsOccupied());
            }
        }
        
        // Case 2: New order has a table and is DINE_IN -> occupy the new table
        if (newTable != null && newIsDineIn) {
            // Only occupy if it's a different table or we're changing from non-DINE_IN to DINE_IN
            if (oldTable == null || !oldTable.getId().equals(newTable.getId()) || !oldWasDineIn) {
                log.info("Occupying new table #{}", newTable.getTableNumber());
                // Refresh table from DB to get latest status
                RestaurantTable tableToOccupy = restaurantTableRepository.findById(newTable.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + newTable.getId()));
                
                log.info("Table #{} current status before occupying: {}, isOccupied: {}", 
                         tableToOccupy.getTableNumber(), 
                         tableToOccupy.getStatus(), 
                         tableToOccupy.getIsOccupied());
                
                // If table is RESERVED, use RestaurantTableService.markAsOccupied() for proper validation
                if (tableToOccupy.getStatus() == TableStatus.RESERVED) {
                    try {
                        RestaurantTable markedTable = restaurantTableService.markAsOccupied(
                            tableToOccupy.getId(), 
                            username
                        );
                        log.info("Reserved table #{} marked as occupied - isOccupied: {}", 
                                 markedTable.getTableNumber(), 
                                 markedTable.getIsOccupied());
                    } catch (IllegalStateException e) {
                        log.error("Cannot occupy reserved table: {}", e.getMessage());
                        throw new IllegalArgumentException(
                            "No se puede ocupar la mesa " + tableToOccupy.getTableNumber() + ": " + e.getMessage()
                        );
                    }
                } else if (tableToOccupy.getStatus() == TableStatus.AVAILABLE) {
                    // Mesa AVAILABLE → cambiar a OCCUPIED (is_occupied se mantiene en false)
                    tableToOccupy.setStatus(TableStatus.OCCUPIED);
                    // NO cambiar is_occupied, debe mantenerse en false
                    tableToOccupy.setUpdatedBy(username);
                    RestaurantTable savedNewTable = restaurantTableRepository.save(tableToOccupy);
                    restaurantTableRepository.flush(); // Force immediate persistence
                    log.info("Available table #{} occupied successfully - Status: {}, isOccupied: {}", 
                             savedNewTable.getTableNumber(), 
                             savedNewTable.getStatus(), 
                             savedNewTable.getIsOccupied());
                } else {
                    // Table is OCCUPIED or OUT_OF_SERVICE
                    throw new IllegalArgumentException(
                        "La mesa " + tableToOccupy.getTableNumber() + " está " + 
                        tableToOccupy.getStatusDisplayName() + " y no se puede asignar"
                    );
                }
            }
        }
        
        // Update order's table reference
        order.setTable(newTable);
    }

    /**
     * Validate customer information based on order type
     * DINE_IN: Customer information is optional
     * DELIVERY: Customer name, phone and address are required
     * TAKEOUT: Customer name and phone are required, address is optional
     */
    private void validateCustomerInformation(Order order) {
        OrderType orderType = order.getOrderType();
        
        if (orderType == OrderType.DELIVERY) {
            // DELIVERY requires: name, phone, address
            if (order.getCustomerName() == null || order.getCustomerName().trim().isEmpty()) {
                throw new IllegalArgumentException("El nombre del cliente es requerido para pedidos a domicilio");
            }
            if (order.getCustomerPhone() == null || order.getCustomerPhone().trim().isEmpty()) {
                throw new IllegalArgumentException("El teléfono del cliente es requerido para pedidos a domicilio");
            }
            if (order.getDeliveryAddress() == null || order.getDeliveryAddress().trim().isEmpty()) {
                throw new IllegalArgumentException("La dirección de entrega es requerida para pedidos a domicilio");
            }
        } else if (orderType == OrderType.TAKEOUT) {
            // TAKEOUT requires: name, phone (address optional)
            if (order.getCustomerName() == null || order.getCustomerName().trim().isEmpty()) {
                throw new IllegalArgumentException("El nombre del cliente es requerido para pedidos para llevar");
            }
            if (order.getCustomerPhone() == null || order.getCustomerPhone().trim().isEmpty()) {
                throw new IllegalArgumentException("El teléfono del cliente es requerido para pedidos para llevar");
            }
        }
        // DINE_IN: No validation needed, customer info is optional
        
        // Set default values for empty fields to avoid null issues
        if (order.getCustomerName() == null) {
            order.setCustomerName("");
        }
        if (order.getCustomerPhone() == null) {
            order.setCustomerPhone("");
        }
        if (order.getDeliveryAddress() == null) {
            order.setDeliveryAddress("");
        }
    }

    /**
     * Validate payment method is enabled
     */
    private void validatePaymentMethod(PaymentMethodType paymentMethod) {
        SystemConfiguration config = systemConfigurationRepository.findAll()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No se encontró la configuración del sistema"
            ));

        if (!config.isPaymentMethodEnabled(paymentMethod)) {
            throw new IllegalStateException(
                String.format("El método de pago '%s' no está habilitado", 
                              paymentMethod.getDisplayName())
            );
        }
    }

    /**
     * Determine if stock should be returned automatically for an item
     * PENDING -> automatic return (never touched)
     * READY + NO requires preparation -> automatic return (auto-advanced, never touched)
     * READY + requires preparation -> manual return (chef prepared it, used ingredients)
     * IN_PREPARATION -> manual return (chef is working on it, may have used ingredients)
     */
    private boolean shouldReturnStockAutomatically(OrderDetail detail) {
        OrderStatus itemStatus = detail.getItemStatus();
        
        // PENDING -> always return automatically (never touched)
        if (itemStatus == OrderStatus.PENDING) {
            return true;
        }
        
        // READY -> depends if chef prepared it or not
        if (itemStatus == OrderStatus.READY) {
            // If item does NOT require preparation -> it was marked READY automatically
            // No ingredients were used, can return automatically
            if (detail.getItemMenu() != null && 
                !Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation())) {
                return true;
            }
            // If item DOES require preparation -> chef prepared it and used ingredients
            // Must be returned MANUALLY
            return false;
        }
        
        // IN_PREPARATION -> requires manual return (chef is working on it)
        return false;
    }

    /**
     * Delete a specific item from an order
     * Only allows deleting items that are not DELIVERED
     * Returns stock automatically if item is PENDING or (READY and !requiresPreparation)
     */
    @Override
    @Transactional
    public OrderDetail deleteOrderItem(Long orderId, Long itemDetailId, String username) {
        log.info("Deleting item {} from order {} by user {}", itemDetailId, orderId, username);

        // Find order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + orderId));

        // Validate order is not in final states
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido CANCELADO");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("No se pueden eliminar items de un pedido PAGADO");
        }

        // Find the item detail in the order
        OrderDetail itemToDelete = order.getOrderDetails().stream()
                .filter(detail -> detail.getIdOrderDetail().equals(itemDetailId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item no encontrado en el pedido"));

        // Validate item is not DELIVERED
        if (itemToDelete.getItemStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("No se puede eliminar un item que ya fue ENTREGADO");
        }

        // Check if this is the last item in the order
        if (order.getOrderDetails().size() == 1) {
            throw new IllegalStateException("LAST_ITEM_CANCEL_ORDER");
        }

        // Check if stock should be returned automatically
        boolean returnStockAuto = shouldReturnStockAutomatically(itemToDelete);
        
        if (returnStockAuto) {
            // Return stock automatically
            ItemMenu itemMenu = itemToDelete.getItemMenu();
            int quantity = itemToDelete.getQuantity();
            
            log.info("Returning stock automatically for item '{}' (quantity: {})", 
                    itemMenu.getName(), quantity);
            
            try {
                returnStockForItem(itemMenu, quantity);
                log.info("Stock returned successfully for item '{}'", itemMenu.getName());
            } catch (Exception e) {
                log.error("Error returning stock for item '{}': {}", itemMenu.getName(), e.getMessage());
                throw new IllegalStateException("Error al devolver el stock: " + e.getMessage());
            }
        } else {
            log.info("Stock must be returned MANUALLY for item '{}' (status: {}, requiresPrep: {})", 
                    itemToDelete.getItemMenu().getName(),
                    itemToDelete.getItemStatus(),
                    itemToDelete.getItemMenu().getRequiresPreparation());
        }

        // Remove item from order
        order.getOrderDetails().remove(itemToDelete);

        // Recalculate order amounts (subtotal, tax, and total)
        // This uses the Order entity's built-in calculation methods:
        // - calculateSubtotal(): sums all item subtotals
        // - calculateTaxAmount(): applies tax rate from system config
        // - calculateTotal(): subtotal + tax
        order.recalculateAmounts();

        // Recalculate order status based on remaining items
        if (!order.getOrderDetails().isEmpty()) {
            OrderStatus newOrderStatus = order.calculateStatusFromItems();
            order.setStatus(newOrderStatus);
            log.info("Order status recalculated to: {}", newOrderStatus);
        } else {
            log.warn("Order {} now has no items after deletion", orderId);
            // Keep current status if no items remain
        }

        // Save changes
        orderRepository.save(order);
        
        log.info("Item '{}' deleted from order {}. New subtotal: {}, New total: {}, New status: {}", 
                itemToDelete.getItemMenu().getName(), 
                order.getOrderNumber(),
                order.getFormattedSubtotal(),
                order.getFormattedTotal(),
                order.getStatus());

        return itemToDelete;
    }
}

