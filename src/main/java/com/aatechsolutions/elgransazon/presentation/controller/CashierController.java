package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Cashier role
 * Handles all cashier operations for order management and payment collection
 */
@Controller
@RequestMapping("/cashier")
@PreAuthorize("hasRole('ROLE_CASHIER')")
@Slf4j
public class CashierController {

    private final CashierOrderServiceImpl cashierOrderService;
    private final OrderService adminOrderService;
    private final RestaurantTableService restaurantTableService;
    private final ItemMenuService itemMenuService;
    private final EmployeeService employeeService;
    private final SystemConfigurationService systemConfigurationService;
    private final CategoryService categoryService;
    private final com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository;
    private final PromotionService promotionService;

    public CashierController(
            @Qualifier("cashierOrderService") CashierOrderServiceImpl cashierOrderService,
            @Qualifier("adminOrderService") OrderService adminOrderService,
            RestaurantTableService restaurantTableService,
            ItemMenuService itemMenuService,
            EmployeeService employeeService,
            SystemConfigurationService systemConfigurationService,
            CategoryService categoryService,
            com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository,
            PromotionService promotionService) {
        this.cashierOrderService = cashierOrderService;
        this.adminOrderService = adminOrderService;
        this.restaurantTableService = restaurantTableService;
        this.itemMenuService = itemMenuService;
        this.employeeService = employeeService;
        this.systemConfigurationService = systemConfigurationService;
        this.categoryService = categoryService;
        this.orderRepository = orderRepository;
        this.promotionService = promotionService;
    }

    /**
     * Display cashier dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Cashier {} accessed dashboard", username);
        
        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        model.addAttribute("config", config);
        model.addAttribute("username", username);
        model.addAttribute("role", "Cajero");
        
        return "cashier/dashboard";
    }

    /**
     * Show list of orders created by current cashier
     * Also shows global unpaid orders (DELIVERED) that can be collected
     */
    @GetMapping("/orders")
    public String listOrders(
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) String date,
            Authentication authentication,
            Model model) {
        
        String username = authentication.getName();
        log.debug("Cashier {} displaying orders list with filters - table: {}, status: {}, type: {}, date: {}", 
                  username, tableId, status, orderType, date);

        // Get orders created by current cashier (like waiter)
        List<Order> myOrders = cashierOrderService.findOrdersByCurrentEmployee();

        // Apply filters to myOrders
        if (date != null && !date.isEmpty()) {
            LocalDateTime startDate = LocalDateTime.parse(date + "T00:00:00");
            LocalDateTime endDate = LocalDateTime.parse(date + "T23:59:59");
            myOrders = myOrders.stream()
                .filter(order -> order.getCreatedAt().isAfter(startDate) && order.getCreatedAt().isBefore(endDate))
                .collect(Collectors.toList());
        }

        if (tableId != null) {
            Long finalTableId = tableId;
            myOrders = myOrders.stream()
                .filter(order -> order.getTable() != null && order.getTable().getId().equals(finalTableId))
                .collect(Collectors.toList());
        }

        if (status != null) {
            myOrders = myOrders.stream()
                .filter(order -> order.getStatus() == status)
                .collect(Collectors.toList());
        }

        if (orderType != null) {
            myOrders = myOrders.stream()
                .filter(order -> order.getOrderType() == orderType)
                .collect(Collectors.toList());
        }

        // Sort by creation date (most recent first)
        myOrders = myOrders.stream()
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());

        // Get global orders (PENDING to PAID) - includes PAID as history
        // Use adminOrderService to get ALL orders (not filtered by employee)
        // EXCLUDE orders created by current cashier (those are already in first table)
        // For PAID orders, only show those collected by current cashier
        List<Order> unpaidOrders = adminOrderService.findAll().stream()
            .filter(o -> {
                // EXCLUDE orders created by current cashier
                if (o.getCreatedBy() != null && o.getCreatedBy().equals(username)) {
                    return false;
                }
                
                // Show all non-PAID orders (created by others)
                if (o.getStatus() == OrderStatus.PENDING ||
                    o.getStatus() == OrderStatus.IN_PREPARATION ||
                    o.getStatus() == OrderStatus.READY ||
                    o.getStatus() == OrderStatus.DELIVERED) {
                    return true;
                }
                
                // For PAID orders, only show if current cashier collected payment
                // (but not created by them - already excluded above)
                if (o.getStatus() == OrderStatus.PAID) {
                    return o.getPaidBy() != null && o.getPaidBy().getUsername().equals(username);
                }
                
                return false;
            })
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());

        // Calculate statistics for current cashier
        long myTodayCount = myOrders.stream()
            .filter(o -> o.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()))
            .count();
        
        // Revenue: All orders PAID today where paidBy = current cashier (regardless of who created them)
        // Only counts order total, NOT including tips
        BigDecimal myTodayRevenue = adminOrderService.findAll().stream()
            .filter(o -> o.getStatus() == OrderStatus.PAID)
            .filter(o -> o.getPaidBy() != null && o.getPaidBy().getUsername().equals(username))
            .filter(o -> o.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()))
            .map(Order::getTotal) // Only order total, NO tip
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long myPendingCount = myOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING)
            .count();
        
        long myPaidCount = myOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PAID)
            .count();
        
        long inPreparationCount = myOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION)
            .count();
        
        long activeCount = myOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING || 
                        o.getStatus() == OrderStatus.IN_PREPARATION || 
                        o.getStatus() == OrderStatus.READY)
            .count();
        
        // Statistics for unpaid orders (global) - exclude PAID from total
        long unpaidCount = unpaidOrders.stream()
            .filter(o -> o.getStatus() != OrderStatus.PAID)
            .count();
        BigDecimal unpaidTotal = unpaidOrders.stream()
            .filter(o -> o.getStatus() != OrderStatus.PAID)
            .map(Order::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Count of PAID orders (history)
        long paidOrdersCount = unpaidOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PAID)
            .count();

        // Get filter data
        List<RestaurantTable> tables = restaurantTableService.findAllOrderByTableNumber();
        OrderStatus[] statuses = OrderStatus.values();
        OrderType[] orderTypes = OrderType.values();

        model.addAttribute("myOrders", myOrders);
        model.addAttribute("unpaidOrders", unpaidOrders);
        model.addAttribute("tables", tables);
        model.addAttribute("statuses", statuses);
        model.addAttribute("orderTypes", orderTypes);
        model.addAttribute("myTodayCount", myTodayCount);
        model.addAttribute("todayRevenue", myTodayRevenue);
        model.addAttribute("myPendingCount", myPendingCount);
        model.addAttribute("myPaidCount", myPaidCount);
        model.addAttribute("inPreparationCount", inPreparationCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("unpaidCount", unpaidCount);
        model.addAttribute("unpaidTotal", unpaidTotal);
        model.addAttribute("paidOrdersCount", paidOrdersCount);
        model.addAttribute("selectedTableId", tableId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedOrderType", orderType);
        model.addAttribute("selectedDate", date);
        model.addAttribute("currentRole", "cashier");

        return "cashier/orders/list";
    }

    /**
     * Show table selection view for DINE_IN orders
     */
    @GetMapping("/orders/select-table")
    public String selectTable(Authentication authentication, Model model) {
        log.debug("Displaying table selection for new order - role: cashier");

        // Get all tables
        List<RestaurantTable> allTables = restaurantTableService.findAllOrderByTableNumber();
        
        // Create a map to store canBeOccupiedNow for each table
        Map<Long, Boolean> canBeOccupiedMap = new HashMap<>();
        
        // Count tables by status and availability
        long availableCount = 0;
        long occupiedCount = 0;
        long reservedNotOccupableCount = 0;
        long canBeOccupiedCount = 0;
        
        for (RestaurantTable table : allTables) {
            boolean canBeOccupied = restaurantTableService.canBeOccupiedNow(table.getId());
            canBeOccupiedMap.put(table.getId(), canBeOccupied);
            
            if (table.getStatus() == TableStatus.AVAILABLE) {
                availableCount++;
            } else if (table.getStatus() == TableStatus.OCCUPIED) {
                occupiedCount++;
            } else if (table.getStatus() == TableStatus.RESERVED) {
                if (canBeOccupied) {
                    canBeOccupiedCount++;
                } else {
                    reservedNotOccupableCount++;
                }
            }
        }

        model.addAttribute("tables", allTables);
        model.addAttribute("canBeOccupiedMap", canBeOccupiedMap);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("occupiedCount", occupiedCount);
        model.addAttribute("reservedCount", reservedNotOccupableCount);
        model.addAttribute("canBeOccupiedCount", canBeOccupiedCount);
        model.addAttribute("totalCount", allTables.size());
        model.addAttribute("currentRole", "cashier");

        return "cashier/orders/order-table-selection";
    }

    /**
     * Show customer information form before order menu
     */
    @GetMapping("/orders/customer-info")
    public String customerInfoForm(
            @RequestParam(required = false) Long tableId,
            @RequestParam String orderType,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying customer info form - OrderType: {}, TableId: {}", orderType, tableId);
        
        try {
            OrderType type = OrderType.valueOf(orderType);
            
            // Validate table for DINE_IN orders
            if (type == OrderType.DINE_IN && tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
                
                if (!cashierOrderService.isTableAvailableForOrder(tableId)) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                        "La mesa seleccionada no está disponible para pedidos");
                    return "redirect:/cashier/orders/select-table";
                }
                
                model.addAttribute("selectedTable", table);  // Cambiar "table" a "selectedTable"
            }
            
            model.addAttribute("orderType", type);
            model.addAttribute("tableId", tableId);
            model.addAttribute("currentRole", "cashier");
            
            return "cashier/orders/order-customer-info";
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid order type: {}", orderType);
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de orden inválido");
            return "redirect:/cashier/orders/select-table";
        }
    }

    /**
     * Show menu items selection with cart
     */
    @GetMapping("/orders/menu")
    public String menuSelection(
            @RequestParam String orderType,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String deliveryAddress,
            @RequestParam(required = false) String deliveryReferences,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying menu selection - OrderType: {}, TableId: {}", orderType, tableId);
        
        try {
            OrderType type = OrderType.valueOf(orderType);
            
            // Validate customer info for TAKEOUT and DELIVERY
            if (type == OrderType.TAKEOUT || type == OrderType.DELIVERY) {
                if (customerName == null || customerName.trim().isEmpty()) {
                    redirectAttributes.addFlashAttribute("errorMessage", 
                        "El nombre del cliente es requerido para pedidos para llevar o delivery");
                    return "redirect:/cashier/orders/customer-info?orderType=" + orderType + 
                           (tableId != null ? "&tableId=" + tableId : "");
                }
            }
            
            // Get table info if DINE_IN
            RestaurantTable selectedTable = null;
            if (type == OrderType.DINE_IN && tableId != null) {
                selectedTable = restaurantTableService.findById(tableId)
                    .orElse(null);
            }
            
            // Update availability for all items based on current stock
            itemMenuService.updateAllItemsAvailability();
            
            // Get all active categories with their menu items
            List<Category> categories = categoryService.getAllActiveCategories();
            
            // Get available menu items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category ID for easier display
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get current employee
            String username = authentication.getName();
            Employee employee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            // Get system configuration for tax rate
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            model.addAttribute("orderType", type);
            model.addAttribute("selectedTable", selectedTable);
            model.addAttribute("customerName", customerName);
            model.addAttribute("customerPhone", customerPhone);
            model.addAttribute("deliveryAddress", deliveryAddress);
            model.addAttribute("deliveryReferences", deliveryReferences);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            model.addAttribute("allItems", availableItems);
            model.addAttribute("employee", employee);
            model.addAttribute("config", config);
            model.addAttribute("taxRate", config.getTaxRate());
            model.addAttribute("currentRole", "cashier");
            
            return "cashier/orders/order-menu";
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid order type: {}", orderType);
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de orden inválido");
            return "redirect:/cashier/orders/select-table";
        }
    }

    /**
     * Create a new order
     */
    @PostMapping("/orders")
    public String createOrder(
            @ModelAttribute("order") Order order,
            BindingResult bindingResult,
            @RequestParam(value = "employeeId", required = true) Long employeeId,
            @RequestParam(value = "tableId", required = false) Long tableId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.info("===== CREATING ORDER =====");
        log.info("Cashier: {}", username);
        log.info("Employee ID (param): {}", employeeId);
        log.info("Order Type: {}", order.getOrderType());
        log.info("Table ID (param): {}", tableId);
        log.info("Table in Order object: {}", order.getTable());
        log.info("Customer Name: {}", order.getCustomerName());
        log.info("Customer Phone: {}", order.getCustomerPhone());
        log.info("Payment Method: {}", order.getPaymentMethod());
        log.info("=========================");

        try {
            // Set employee
            Employee employee = employeeService.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + employeeId));
            order.setEmployee(employee);
            
            // Set table if provided (required for DINE_IN)
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + tableId));
                order.setTable(table);
            }
            
            // Build order details
            List<OrderDetail> orderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds);

            if (orderDetails.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Debe agregar al menos un item al pedido");
                return "redirect:/cashier/orders/menu?orderType=" + order.getOrderType() + 
                       (tableId != null ? "&tableId=" + tableId : "");
            }

            // Set audit fields
            order.setCreatedBy(username);

            // Create the order
            Order createdOrder = cashierOrderService.create(order, orderDetails);

            log.info("Order created successfully by cashier: {}", createdOrder.getOrderNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido creado exitosamente: " + createdOrder.getOrderNumber());

            return "redirect:/cashier/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error creating order: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cashier/orders/menu?orderType=" + order.getOrderType() + 
                   (tableId != null ? "&tableId=" + tableId : "");
        } catch (Exception e) {
            log.error("Error creating order", e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al crear el pedido: " + e.getMessage());
            return "redirect:/cashier/orders/select-table";
        }
    }

    /**
     * View order details
     */
    @GetMapping("/orders/view/{id}")
    public String viewOrder(
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Viewing order details. ID: {}", id);

        return cashierOrderService.findByIdWithDetails(id)
                .map(order -> {
                    model.addAttribute("order", order);
                    model.addAttribute("orderDetails", order.getOrderDetails());
                    model.addAttribute("currentRole", "cashier");
                    return "cashier/orders/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Pedido no encontrado");
                    return "redirect:/cashier/orders";
                });
    }

    /**
     * Show edit form for an order (only PENDING status)
     */
    @GetMapping("/orders/edit/{id}")
    public String editOrderForm(
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.debug("Cashier {} accessing edit form for order {}", username, id);

        try {
            Order order = cashierOrderService.findByIdWithDetails(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Cannot edit PAID or CANCELLED orders
            if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No se pueden editar pedidos PAGADOS o CANCELADOS");
                return "redirect:/cashier/orders";
            }

            // Get current employee
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

            // Get available tables (for DINE_IN orders)
            List<RestaurantTable> availableTables = restaurantTableService.findAllOrderByTableNumber();

            // Get available menu items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Convert to simple DTOs to avoid circular reference issues
            List<Map<String, Object>> availableItemsDTO = availableItems.stream()
                    .map(item -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("idItemMenu", item.getIdItemMenu());
                        dto.put("name", item.getName());
                        dto.put("price", item.getPrice());
                        return dto;
                    })
                    .collect(Collectors.toList());

            // Get system configuration for tax rate
            SystemConfiguration config = systemConfigurationService.getConfiguration();

            // Get enabled payment methods from system configuration
            Map<PaymentMethodType, Boolean> paymentMethodsMap = config.getPaymentMethods();
            List<PaymentMethodType> enabledPaymentMethods = paymentMethodsMap.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            // Convert order details to simple DTOs
            List<Map<String, Object>> orderDetailsDTO = order.getOrderDetails().stream()
                    .map(detail -> {
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("quantity", detail.getQuantity());
                        dto.put("comments", detail.getComments() != null ? detail.getComments() : "");
                        
                        // Create itemMenu DTO
                        Map<String, Object> itemMenuDTO = new HashMap<>();
                        itemMenuDTO.put("idItemMenu", detail.getItemMenu().getIdItemMenu());
                        itemMenuDTO.put("name", detail.getItemMenu().getName());
                        itemMenuDTO.put("price", detail.getItemMenu().getPrice());
                        
                        dto.put("itemMenu", itemMenuDTO);
                        return dto;
                    })
                    .collect(Collectors.toList());

            model.addAttribute("order", order);
            model.addAttribute("orderDetails", orderDetailsDTO);
            model.addAttribute("employee", employee);
            model.addAttribute("availableTables", availableTables);
            model.addAttribute("availableItems", availableItemsDTO);
            model.addAttribute("taxRate", config.getTaxRate());
            model.addAttribute("orderTypes", OrderType.values());
            model.addAttribute("paymentMethods", enabledPaymentMethods);
            model.addAttribute("formAction", "/cashier/orders/edit/" + id);
            model.addAttribute("currentRole", "cashier");

            return "cashier/orders/form";

        } catch (IllegalArgumentException e) {
            log.error("Error accessing edit form: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cashier/orders";
        } catch (Exception e) {
            log.error("Error loading edit form for order {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al cargar el formulario de edición");
            return "redirect:/cashier/orders";
        }
    }

    /**
     * Update an existing order (only PENDING status)
     */
    @PostMapping("/orders/edit/{id}")
    public String updateOrder(
            @PathVariable Long id,
            @ModelAttribute("order") Order order,
            @RequestParam(value = "tableId", required = false) Long tableId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.info("Cashier {} updating order {}", username, id);

        try {
            // Get existing order
            Order existingOrder = cashierOrderService.findByIdWithDetails(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Only allow editing PENDING orders
            if (existingOrder.getStatus() != OrderStatus.PENDING) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Solo se pueden editar pedidos en estado PENDIENTE");
                return "redirect:/cashier/orders";
            }

            // Set table if provided
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                        .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
                order.setTable(table);
            } else {
                order.setTable(null);
            }

            // Keep the same employee (creator)
            order.setEmployee(existingOrder.getEmployee());

            // Update basic order info
            existingOrder.setOrderType(order.getOrderType());
            existingOrder.setTable(order.getTable());
            existingOrder.setCustomerName(order.getCustomerName());
            existingOrder.setCustomerPhone(order.getCustomerPhone());
            existingOrder.setDeliveryAddress(order.getDeliveryAddress());
            existingOrder.setDeliveryReferences(order.getDeliveryReferences());
            existingOrder.setPaymentMethod(order.getPaymentMethod());
            existingOrder.setUpdatedBy(username);

            // Build new order details (items are replaced, not edited individually)
            List<OrderDetail> newOrderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds);

            if (newOrderDetails.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Debe agregar al menos un item al pedido");
                return "redirect:/cashier/orders/edit/" + id;
            }

            // Update the order
            Order updatedOrder = cashierOrderService.update(id, existingOrder, newOrderDetails);

            log.info("Order {} updated successfully by cashier {}", updatedOrder.getOrderNumber(), username);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido actualizado exitosamente: " + updatedOrder.getOrderNumber());

            return "redirect:/cashier/orders";

        } catch (IllegalArgumentException e) {
            log.error("Validation error updating order: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/cashier/orders/edit/" + id;
        } catch (Exception e) {
            log.error("Error updating order {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error al actualizar el pedido: " + e.getMessage());
            return "redirect:/cashier/orders";
        }
    }

    /**
     * Change order status (AJAX) - Cashier can only mark DELIVERED as PAID
     */
    @PostMapping("/orders/{id}/change-status")
    @ResponseBody
    public Map<String, Object> changeStatus(
            @PathVariable Long id,
            @RequestParam String newStatus,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cashier {} changing order {} status to {}", username, id, newStatus);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderStatus status = OrderStatus.valueOf(newStatus);
            
            // Get current employee
            Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            // Get the order to check current state
            Order order = cashierOrderService.findByIdOrThrow(id);
            
            // Cashier can collect payment with ANY payment method (including CASH)
            if (status == OrderStatus.PAID && order.getPaidBy() == null) {
                order.setPaidBy(currentEmployee);
                orderRepository.save(order);
                log.info("Setting paidBy to cashier: {}", username);
            }
            
            // Now change the status
            Order updated = cashierOrderService.changeStatus(id, status, username);
            
            response.put("success", true);
            response.put("message", "Estado del pedido cambiado a " + status.getDisplayName());
            response.put("order", buildOrderDTO(updated));
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", newStatus);
            response.put("success", false);
            response.put("message", "Estado inválido: " + newStatus);
        } catch (IllegalStateException e) {
            log.error("Error changing order status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error changing order status", e);
            response.put("success", false);
            response.put("message", "Error al cambiar el estado del pedido: " + e.getMessage());
        }

        return response;
    }

    /**
     * Get valid next statuses for an order (AJAX)
     * Cashier can:
     * - Mark READY orders as DELIVERED
     * - Mark DELIVERED orders as PAID (any payment method)
     */
    @GetMapping("/orders/{id}/valid-statuses")
    @ResponseBody
    public Map<String, Object> getValidStatuses(
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.debug("Cashier {} getting valid statuses for order {}", username, id);

        Map<String, Object> response = new HashMap<>();
        
        try {
            Order order = cashierOrderService.findByIdOrThrow(id);
            
            // Check if the cashier created this order
            boolean isOrderCreator = order.getCreatedBy().equals(username);
            
            List<Map<String, String>> validStatuses = new ArrayList<>();
            
            // Cashier can mark READY → DELIVERED for TAKEOUT and DINE_IN orders
            // (DELIVERY orders are marked as DELIVERED by delivery drivers)
            if (order.getStatus() == OrderStatus.READY && 
                (order.getOrderType() == OrderType.TAKEOUT || order.getOrderType() == OrderType.DINE_IN)) {
                Map<String, String> deliveredStatus = new HashMap<>();
                deliveredStatus.put("value", OrderStatus.DELIVERED.name());
                deliveredStatus.put("label", OrderStatus.DELIVERED.getDisplayName());
                validStatuses.add(deliveredStatus);
            }
            
            // Cashier can ALWAYS mark DELIVERED orders as PAID (even if they didn't create it)
            if (order.getStatus() == OrderStatus.DELIVERED) {
                Map<String, String> paidStatus = new HashMap<>();
                paidStatus.put("value", OrderStatus.PAID.name());
                paidStatus.put("label", OrderStatus.PAID.getDisplayName());
                validStatuses.add(paidStatus);
            }
            
            response.put("success", true);
            response.put("validStatuses", validStatuses);
            response.put("currentStatus", order.getStatus().name());
            response.put("currentStatusLabel", order.getStatus().getDisplayName());
            response.put("canMarkAsPaid", order.getStatus() == OrderStatus.DELIVERED); // Only DELIVERED can be marked as PAID
            response.put("canMarkAsDelivered", order.getStatus() == OrderStatus.READY && 
                (order.getOrderType() == OrderType.TAKEOUT || order.getOrderType() == OrderType.DINE_IN)); // Can mark as delivered if READY and not DELIVERY
            response.put("isOrderCreator", isOrderCreator); // Tell frontend if cashier created this order
            
        } catch (Exception e) {
            log.error("Error getting valid statuses for order {}", id, e);
            response.put("success", false);
            response.put("message", "Error al obtener estados válidos: " + e.getMessage());
            response.put("validStatuses", new ArrayList<>());
        }

        return response;
    }

    /**
     * Cancel an order (AJAX)
     */
    @PostMapping("/orders/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancelOrder(
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cashier {} cancelling order {}", username, id);

        Map<String, Object> response = new HashMap<>();
        
        try {
            Order cancelled = cashierOrderService.cancel(id, username);
            response.put("success", true);
            response.put("message", "Pedido " + cancelled.getOrderNumber() + " cancelado exitosamente");
            response.put("order", buildOrderDTO(cancelled));
            
            // Analyze items to determine stock return information
            String stockInfo = analyzeStockReturn(cancelled);
            if (stockInfo != null && !stockInfo.isEmpty()) {
                response.put("stockInfo", stockInfo);
            }
        } catch (IllegalStateException e) {
            log.error("Error cancelling order: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error cancelling order", e);
            response.put("success", false);
            response.put("message", "Error al cancelar el pedido: " + e.getMessage());
        }

        return response;
    }

    /**
     * Delete a specific item from an order (AJAX)
     */
    @DeleteMapping("/orders/{orderId}/items/{itemId}")
    @ResponseBody
    public Map<String, Object> deleteOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cashier {} deleting item {} from order {}", username, itemId, orderId);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderDetail deletedItem = cashierOrderService.deleteOrderItem(orderId, itemId, username);
            
            // Get updated order
            Order order = cashierOrderService.findByIdOrThrow(orderId);
            
            // Analyze stock return for this specific item
            String stockInfo = analyzeItemStockReturn(deletedItem);
            
            response.put("success", true);
            response.put("message", String.format("Item '%s' eliminado del pedido exitosamente", 
                    deletedItem.getItemMenu().getName()));
            response.put("stockInfo", stockInfo);
            response.put("orderTotal", order.getTotal());
            response.put("orderStatus", order.getStatus().name());
            response.put("orderStatusLabel", order.getStatus().getDisplayName());
            response.put("remainingItems", order.getOrderDetails().size());
            
        } catch (IllegalArgumentException e) {
            log.error("Item not found: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (IllegalStateException e) {
            log.error("Cannot delete item: {}", e.getMessage());
            
            // Check if this is the last item scenario
            if ("LAST_ITEM_CANCEL_ORDER".equals(e.getMessage())) {
                response.put("success", false);
                response.put("isLastItem", true);
                response.put("message", "Este es el último item de la orden. ¿Deseas cancelar la orden completa?");
            } else {
                response.put("success", false);
                response.put("message", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error deleting item from order", e);
            response.put("success", false);
            response.put("message", "Error al eliminar el item: " + e.getMessage());
        }

        return response;
    }

    /**
     * Display user profile
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return profile view
     */
    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("User {} accessed profile", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            model.addAttribute("employee", employee);
            return "cashier/profile/view";
            
        } catch (Exception e) {
            log.error("Error loading profile for user {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el perfil");
            return "redirect:/cashier/dashboard";
        }
    }

    /**
     * Display menu items (visual only)
     *
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return menu view
     */
    @GetMapping("/menu/view")
    public String viewMenu(Model model, RedirectAttributes redirectAttributes) {
        log.info("Accessed visual menu view");
        
        try {
            // Get system configuration
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            // Get all active categories with their menu items
            List<Category> categories = categoryService.getAllActiveCategories();
            
            // Get available menu items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category ID for easier display
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            
            return "cashier/menu/view";
            
        } catch (Exception e) {
            log.error("Error loading menu view: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el menú");
            return "redirect:/cashier/dashboard";
        }
    }

    /**
     * Display user reports with charts
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return reports view
     */
    @GetMapping("/reports/view")
    public String viewReports(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("User {} accessed reports view", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // Get all orders collected by this cashier (PAID orders where paidBy = current cashier)
            List<Order> collectedOrders = orderRepository.findAll().stream()
                    .filter(order -> order.getStatus() == OrderStatus.PAID)
                    .filter(order -> order.getPaidBy() != null && order.getPaidBy().getIdEmpleado().equals(employee.getIdEmpleado()))
                    .toList();
            
            // Get today's date
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDateTime startOfDay = today.atStartOfDay();
            java.time.LocalDateTime endOfDay = today.atTime(java.time.LocalTime.MAX);
            
            // Today's collected orders
            List<Order> todaysCollectedOrders = collectedOrders.stream()
                    .filter(order -> {
                        java.time.LocalDateTime paidAt = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                        return paidAt.isAfter(startOfDay) && paidAt.isBefore(endOfDay);
                    })
                    .toList();
            
            // Calculate statistics
            // Total collected revenue (only order total, not tips)
            BigDecimal totalRevenue = collectedOrders.stream()
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Today's collected revenue
            BigDecimal todayRevenue = todaysCollectedOrders.stream()
                    .map(Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Total tips collected
            BigDecimal totalTips = collectedOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Today's tips
            BigDecimal todayTips = todaysCollectedOrders.stream()
                    .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Average values
            BigDecimal averageOrderValue = collectedOrders.size() > 0
                    ? totalRevenue.divide(BigDecimal.valueOf(collectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal todayAverageOrderValue = todaysCollectedOrders.size() > 0
                    ? todayRevenue.divide(BigDecimal.valueOf(todaysCollectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal averageTip = collectedOrders.size() > 0
                    ? totalTips.divide(BigDecimal.valueOf(collectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            BigDecimal todayAverageTip = todaysCollectedOrders.size() > 0
                    ? todayTips.divide(BigDecimal.valueOf(todaysCollectedOrders.size()), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            
            // Order counts
            int totalOrders = collectedOrders.size();
            int todayOrders = todaysCollectedOrders.size();
            
            // Last 7 days data
            List<String> last7DaysLabels = new ArrayList<>();
            List<Long> last7DaysOrdersData = new ArrayList<>();
            List<BigDecimal> last7DaysRevenueData = new ArrayList<>();
            List<BigDecimal> last7DaysTipsData = new ArrayList<>();
            
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
            
            for (int i = 6; i >= 0; i--) {
                java.time.LocalDate date = today.minusDays(i);
                java.time.LocalDateTime dayStart = date.atStartOfDay();
                java.time.LocalDateTime dayEnd = date.atTime(java.time.LocalTime.MAX);
                
                List<Order> dayOrders = collectedOrders.stream()
                        .filter(order -> {
                            java.time.LocalDateTime paidAt = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                            return paidAt.isAfter(dayStart) && paidAt.isBefore(dayEnd);
                        })
                        .toList();
                
                last7DaysLabels.add(date.format(formatter));
                last7DaysOrdersData.add((long) dayOrders.size());
                
                BigDecimal dayRevenue = dayOrders.stream()
                        .map(Order::getTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                last7DaysRevenueData.add(dayRevenue);
                
                BigDecimal dayTips = dayOrders.stream()
                        .map(order -> order.getTip() != null ? order.getTip() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                last7DaysTipsData.add(dayTips);
            }
            
            // Add all attributes to model
            model.addAttribute("employee", employee);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("todayOrders", todayOrders);
            model.addAttribute("totalRevenue", totalRevenue);
            model.addAttribute("todayRevenue", todayRevenue);
            model.addAttribute("totalTips", totalTips);
            model.addAttribute("todayTips", todayTips);
            model.addAttribute("averageOrderValue", averageOrderValue);
            model.addAttribute("todayAverageOrderValue", todayAverageOrderValue);
            model.addAttribute("averageTip", averageTip);
            model.addAttribute("todayAverageTip", todayAverageTip);
            
            // Chart data
            model.addAttribute("last7DaysLabels", last7DaysLabels);
            model.addAttribute("last7DaysOrdersData", last7DaysOrdersData);
            model.addAttribute("last7DaysRevenueData", last7DaysRevenueData);
            model.addAttribute("last7DaysTipsData", last7DaysTipsData);
            
            // Status counts - For cashier, we don't track status by employee
            // Set all to 0 since cashier doesn't create orders with different statuses
            model.addAttribute("totalPending", 0);
            model.addAttribute("totalInPreparation", 0);
            model.addAttribute("totalReady", 0);
            model.addAttribute("totalDelivered", 0);
            model.addAttribute("totalPaid", (long) totalOrders);
            model.addAttribute("totalCancelled", 0);
            
            model.addAttribute("todayPending", 0);
            model.addAttribute("todayInPreparation", 0);
            model.addAttribute("todayReady", 0);
            model.addAttribute("todayDelivered", 0);
            model.addAttribute("todayPaid", (long) todayOrders);
            model.addAttribute("todayCancelled", 0);
            
            return "cashier/reports/view";
            
        } catch (Exception e) {
            log.error("Error loading reports for user {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar los reportes");
            return "redirect:/cashier/dashboard";
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Build order details from form data
     */
    private List<OrderDetail> buildOrderDetails(
            List<Long> itemIds,
            List<Integer> quantities,
            List<String> comments,
            List<String> promotionPrices,
            List<String> promotionIds) {
        
        List<OrderDetail> orderDetails = new ArrayList<>();

        if (itemIds != null && !itemIds.isEmpty()) {
            for (int i = 0; i < itemIds.size(); i++) {
                Long itemId = itemIds.get(i);
                Integer quantity = quantities.get(i);
                String comment = (comments != null && i < comments.size()) ? comments.get(i) : null;
                String promotionPriceStr = (promotionPrices != null && i < promotionPrices.size()) ? promotionPrices.get(i) : null;
                String promotionIdStr = (promotionIds != null && i < promotionIds.size()) ? promotionIds.get(i) : null;

                ItemMenu item = itemMenuService.findById(itemId)
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));

                OrderDetail.OrderDetailBuilder detailBuilder = OrderDetail.builder()
                        .itemMenu(item)
                        .quantity(quantity)
                        .unitPrice(item.getPrice())
                        .comments(comment);
                
                // Set item status based on whether it requires preparation
                if (Boolean.TRUE.equals(item.getRequiresPreparation())) {
                    detailBuilder.itemStatus(OrderStatus.PENDING);
                } else {
                    detailBuilder.itemStatus(OrderStatus.READY);
                }
                
                // BACKEND VALIDATION: Validate and recalculate promotion price
                if (promotionIdStr != null && !promotionIdStr.trim().isEmpty()) {
                    try {
                        Long promotionId = Long.parseLong(promotionIdStr);
                        
                        // Fetch promotion from database to validate it exists and is active
                        Promotion promotion = promotionService.findById(promotionId)
                            .orElse(null);
                        
                        if (promotion != null && promotion.isValidNow()) {
                            // Validate that the promotion applies to this item
                            boolean promotionAppliesToItem = promotion.getItems().stream()
                                .anyMatch(promotionItem -> promotionItem.getIdItemMenu().equals(itemId));
                            
                            if (promotionAppliesToItem) {
                                // SECURITY: Recalculate price in backend (don't trust frontend)
                                BigDecimal calculatedDiscountedTotal = promotion.calculateDiscountedPrice(
                                    item.getPrice(), 
                                    quantity
                                );
                                
                                // Calculate price per unit with discount
                                BigDecimal calculatedPricePerUnit = calculatedDiscountedTotal
                                    .divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
                                
                                log.info("CASHIER BACKEND VALIDATION - Item: {}, Qty: {}, Promotion: {}, " +
                                        "Original Price/Unit: ${}, Calculated Price/Unit: ${}, " +
                                        "Calculated Total: ${}", 
                                        item.getName(), quantity, promotion.getName(),
                                        item.getPrice(), calculatedPricePerUnit, calculatedDiscountedTotal);
                                
                                // Set the VALIDATED promotion data
                                detailBuilder.appliedPromotionId(promotionId);
                                detailBuilder.promotionAppliedPrice(calculatedPricePerUnit);
                                
                                // Validate minimum quantity for BUY_X_PAY_Y promotions
                                if (promotion.getPromotionType() == PromotionType.BUY_X_PAY_Y) {
                                    if (quantity < promotion.getBuyQuantity()) {
                                        log.warn("Quantity {} is less than required {} for promotion {}. Applying no promotion.",
                                                quantity, promotion.getBuyQuantity(), promotion.getName());
                                        // Don't apply promotion if minimum quantity not met
                                        detailBuilder.appliedPromotionId(null);
                                        detailBuilder.promotionAppliedPrice(null);
                                    }
                                }
                            } else {
                                log.warn("Promotion {} does not apply to item {}", promotionId, item.getName());
                            }
                        } else {
                            log.warn("Promotion {} is not valid or not active", promotionId);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion ID format: {}", promotionIdStr);
                    }
                }
                
                OrderDetail detail = detailBuilder.build();
                detail.calculateSubtotal();
                orderDetails.add(detail);
            }
        }

        return orderDetails;
    }

    /**
     * Build a simple DTO for an order to send in AJAX responses
     */
    private Map<String, Object> buildOrderDTO(Order order) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", order.getIdOrder());
        dto.put("orderNumber", order.getOrderNumber());
        dto.put("status", order.getStatus().name());
        dto.put("statusLabel", order.getStatus().getDisplayName());
        dto.put("orderType", order.getOrderType().name());
        dto.put("orderTypeLabel", order.getOrderType().getDisplayName());
        dto.put("total", order.getTotal());
        dto.put("canBeCancelled", order.getStatus().canBeCancelled());
        
        if (order.getTable() != null) {
            dto.put("tableNumber", order.getTable().getTableNumber());
        }
        
        // Who created the order
        if (order.getEmployee() != null) {
            dto.put("createdBy", order.getEmployee().getFullName());
        }
        
        // Who prepared the order
        if (order.getPreparedBy() != null) {
            dto.put("preparedBy", order.getPreparedBy().getFullName());
        }
        
        // Who collected payment
        if (order.getPaidBy() != null) {
            dto.put("paidBy", order.getPaidBy().getFullName());
        }
        
        return dto;
    }

    /**
     * Analyze items to determine stock return information
     * Returns a message describing how stock was handled
     */
    private String analyzeStockReturn(Order order) {
        if (order.getOrderDetails() == null || order.getOrderDetails().isEmpty()) {
            return null;
        }

        int automaticItems = 0;
        int manualItems = 0;

        for (OrderDetail detail : order.getOrderDetails()) {
            OrderStatus itemStatus = detail.getItemStatus();
            
            // PENDING -> always automatic
            if (itemStatus == OrderStatus.PENDING) {
                automaticItems++;
                continue;
            }
            
            // READY -> check if requires preparation
            if (itemStatus == OrderStatus.READY) {
                if (detail.getItemMenu() != null && 
                    !Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation())) {
                    // Auto-advanced to READY, never touched by chef
                    automaticItems++;
                } else {
                    // Chef prepared it, used ingredients
                    manualItems++;
                }
                continue;
            }
            
            // IN_PREPARATION -> always manual
            if (itemStatus == OrderStatus.IN_PREPARATION) {
                manualItems++;
            }
        }

        // Build appropriate message
        if (automaticItems > 0 && manualItems == 0) {
            return "✅ Stock devuelto automáticamente para todos los items (" + automaticItems + " items)";
        } else if (manualItems > 0 && automaticItems == 0) {
            return "⚠️ Stock debe ser devuelto manualmente para todos los items (" + manualItems + " items)";
        } else if (automaticItems > 0 && manualItems > 0) {
            return "ℹ️ Stock devuelto: " + automaticItems + " items automáticos, " + 
                   manualItems + " items requieren devolución manual";
        }

        return null;
    }

    /**
     * Analyze stock return for a single item
     * Returns a message describing how stock was handled for this specific item
     */
    private String analyzeItemStockReturn(OrderDetail detail) {
        OrderStatus itemStatus = detail.getItemStatus();
        
        // PENDING -> always automatic
        if (itemStatus == OrderStatus.PENDING) {
            return "✅ Stock devuelto automáticamente (item nunca fue preparado)";
        }
        
        // READY -> check if requires preparation
        if (itemStatus == OrderStatus.READY) {
            if (detail.getItemMenu() != null && 
                !Boolean.TRUE.equals(detail.getItemMenu().getRequiresPreparation())) {
                return "✅ Stock devuelto automáticamente (item no requiere preparación)";
            } else {
                return "⚠️ Stock debe ser devuelto manualmente (chef ya preparó el item)";
            }
        }
        
        // IN_PREPARATION -> always manual
        if (itemStatus == OrderStatus.IN_PREPARATION) {
            return "⚠️ Stock debe ser devuelto manualmente (item estaba en preparación)";
        }

        return "ℹ️ Revisar devolución de stock manualmente";
    }
}

