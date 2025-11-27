package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
 * Controller for Order (Pedidos) management
 * Handles CRUD operations for customer orders
 * Uses different OrderService implementations based on user role
 */
@Controller
@RequestMapping("/{role}/orders")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER', 'ROLE_CHEF', 'ROLE_DELIVERY', 'ROLE_CASHIER')")
@Slf4j
public class OrderController {

    private final Map<String, OrderService> orderServices;
    private final RestaurantTableService restaurantTableService;
    private final ItemMenuService itemMenuService;
    private final EmployeeService employeeService;
    private final SystemConfigurationService systemConfigurationService;
    private final CategoryService categoryService;
    private final com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository;
    private final PromotionService promotionService;
    private final WebSocketNotificationService wsNotificationService;

    /**
     * Constructor with dependency injection
     * Injects admin, waiter, chef, delivery and cashier order services
     * MANAGER uses the same service as ADMIN
     */
    public OrderController(
            @Qualifier("adminOrderService") OrderService adminOrderService,
            @Qualifier("waiterOrderService") OrderService waiterOrderService,
            @Qualifier("chefOrderService") OrderService chefOrderService,
            @Qualifier("deliveryOrderService") OrderService deliveryOrderService,
            @Qualifier("cashierOrderService") OrderService cashierOrderService,
            RestaurantTableService restaurantTableService,
            ItemMenuService itemMenuService,
            EmployeeService employeeService,
            SystemConfigurationService systemConfigurationService,
            CategoryService categoryService,
            com.aatechsolutions.elgransazon.domain.repository.OrderRepository orderRepository,
            PromotionService promotionService,
            WebSocketNotificationService wsNotificationService) {
        
        this.orderServices = Map.of(
            "admin", adminOrderService,
            "manager", adminOrderService,  // MANAGER uses admin service
            "waiter", waiterOrderService,
            "chef", chefOrderService,
            "delivery", deliveryOrderService,
            "cashier", cashierOrderService
        );
        this.restaurantTableService = restaurantTableService;
        this.itemMenuService = itemMenuService;
        this.employeeService = employeeService;
        this.systemConfigurationService = systemConfigurationService;
        this.categoryService = categoryService;
        this.orderRepository = orderRepository;
        this.promotionService = promotionService;
        this.wsNotificationService = wsNotificationService;
    }

    /**
     * Get the correct OrderService based on role
     */
    private OrderService getOrderService(String role) {
        OrderService service = orderServices.get(role.toLowerCase());
        if (service == null) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
        return service;
    }

    /**
     * Validate role path variable matches user's actual role
     * MANAGER can use admin routes
     */
    private void validateRole(String role, Authentication authentication) {
        String userRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ROLE_MANAGER") || 
                               auth.equals("ROLE_WAITER") || auth.equals("ROLE_CHEF") || 
                               auth.equals("ROLE_DELIVERY") || auth.equals("ROLE_CASHIER"))
                .map(auth -> auth.replace("ROLE_", "").toLowerCase())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("User has no valid role"));
        
        // MANAGER can access admin routes
        if (userRole.equals("manager") && role.equalsIgnoreCase("admin")) {
            return; // Allow MANAGER to use admin routes
        }
        
        if (!role.equalsIgnoreCase(userRole)) {
            throw new IllegalStateException("Access denied: Role mismatch");
        }
    }

    /**
     * Show list of all orders with filters
     */
    @GetMapping
    public String listOrders(
            @PathVariable String role,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) String date,
            Authentication authentication,
            Model model) {
        
        log.debug("Displaying orders list with filters - role: {}, table: {}, status: {}, type: {}, date: {}", 
                  role, tableId, status, orderType, date);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        List<Order> orders;

        // Apply filters
        if (date != null && !date.isEmpty()) {
            LocalDateTime startDate = LocalDateTime.parse(date + "T00:00:00");
            LocalDateTime endDate = LocalDateTime.parse(date + "T23:59:59");
            orders = orderService.findByDateRange(startDate, endDate);
        } else {
            orders = orderService.findAll(); // Already filtered by role in service implementation
        }

        // Filter by table
        if (tableId != null) {
            orders = orders.stream()
                .filter(order -> order.getTable() != null && order.getTable().getId().equals(tableId))
                .collect(Collectors.toList());
        }

        // Filter by status
        if (status != null) {
            orders = orders.stream()
                .filter(order -> order.getStatus() == status)
                .collect(Collectors.toList());
        }

        // Filter by order type
        if (orderType != null) {
            orders = orders.stream()
                .filter(order -> order.getOrderType() == orderType)
                .collect(Collectors.toList());
        }

        // Sort by creation date (most recent first)
        orders = orders.stream()
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()))
            .collect(Collectors.toList());

        // Calculate statistics
        long todayCount = orderService.countTodaysOrders();
        BigDecimal todayRevenue = orderService.getTodaysRevenue();
        long pendingCount = orderService.countByStatus(OrderStatus.PENDING);
        long inPreparationCount = orderService.countByStatus(OrderStatus.IN_PREPARATION);
        long activeCount = orderService.findActiveOrders().size();

        // Get filter data
        List<RestaurantTable> tables = restaurantTableService.findAllOrderByTableNumber();
        OrderStatus[] statuses = OrderStatus.values();
        OrderType[] orderTypes = OrderType.values();

        model.addAttribute("orders", orders);
        model.addAttribute("tables", tables);
        model.addAttribute("statuses", statuses);
        model.addAttribute("orderTypes", orderTypes);
        model.addAttribute("todayCount", todayCount);
        model.addAttribute("todayRevenue", todayRevenue);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("inPreparationCount", inPreparationCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("selectedTableId", tableId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedOrderType", orderType);
        model.addAttribute("selectedDate", date);
        model.addAttribute("currentRole", role);

        return role + "/orders/list";
    }

    /**
     * Show table selection view for DINE_IN orders
     */
    @GetMapping("/select-table")
    public String selectTable(@PathVariable String role, Authentication authentication, Model model) {
        log.debug("Displaying table selection for new order - role: {}", role);

        // Validate role
        validateRole(role, authentication);

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
            boolean canOccupy = restaurantTableService.canBeOccupiedNow(table.getId());
            canBeOccupiedMap.put(table.getId(), canOccupy);
            
            if (table.getStatus() == TableStatus.AVAILABLE) {
                availableCount++;
            } else if (table.getStatus() == TableStatus.OCCUPIED) {
                occupiedCount++;
            } else if (table.getStatus() == TableStatus.RESERVED) {
                if (canOccupy) {
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
        model.addAttribute("currentRole", role);

        return role + "/orders/order-table-selection";
    }

    /**
     * Show customer information form before order menu
     */
    @GetMapping("/customer-info")
    public String customerInfoForm(
            @PathVariable String role,
            @RequestParam(required = false) Long tableId,
            @RequestParam String orderType,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying customer info form - role: {}, OrderType: {}, TableId: {}", role, orderType, tableId);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        // Validate orderType
        OrderType type;
        try {
            type = OrderType.valueOf(orderType);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de pedido inválido");
            return "redirect:/" + role + "/orders/select-table";
        }

        // If DINE_IN, validate table
        RestaurantTable selectedTable = null;
        if (type == OrderType.DINE_IN) {
            if (tableId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Debe seleccionar una mesa para pedidos en restaurante");
                return "redirect:/" + role + "/orders/select-table";
            }
            
            selectedTable = restaurantTableService.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
            
            // Validate table availability
            if (!orderService.isTableAvailableForOrder(tableId)) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "La mesa #" + selectedTable.getTableNumber() + " no está disponible");
                return "redirect:/" + role + "/orders/select-table";
            }
        }

        model.addAttribute("orderType", type);
        model.addAttribute("selectedTable", selectedTable);
        model.addAttribute("currentRole", role);

        return role + "/orders/order-customer-info";
    }

    /**
     * Show menu items selection with cart
     */
    @GetMapping("/menu")
    public String menuSelection(
            @PathVariable String role,
            @RequestParam String orderType,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String customerPhone,
            @RequestParam(required = false) String deliveryAddress,
            @RequestParam(required = false) String deliveryReferences,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying menu selection - role: {}, OrderType: {}, TableId: {}", role, orderType, tableId);

        // Validate role
        validateRole(role, authentication);

        // Get logged-in employee
        String username = authentication.getName();
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado para usuario: " + username));

        // Validate orderType
        OrderType type;
        try {
            type = OrderType.valueOf(orderType);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tipo de pedido inválido");
            return "redirect:/" + role + "/orders/select-table";
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
        
        // Get available menu items grouped by category
        List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
        
        // Group items by category for easier display
        Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
            .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));

        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Get enabled payment methods from configuration
        Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
        List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(PaymentMethodType::name))
                .collect(Collectors.toList());
        
        // Validate at least one payment method is enabled
        if (enabledPaymentMethods.isEmpty()) {
            log.warn("No payment methods enabled in system configuration");
            redirectAttributes.addFlashAttribute("errorMessage", "No hay métodos de pago habilitados. Por favor contacte al administrador.");
            return "redirect:/" + role + "/orders";
        }

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
        model.addAttribute("currentRole", role);
        model.addAttribute("config", config);
        model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
        
        // Add active promotions for items
        List<Promotion> activePromotions = promotionService.findActivePromotions();
        model.addAttribute("activePromotions", activePromotions);

        return role + "/orders/order-menu";
    }

    /**
     * Show menu to add items to existing order
     * GET /{role}/orders/{orderId}/add-items
     */
    @GetMapping("/{orderId}/add-items")
    public String showMenuToAddItems(
            @PathVariable String role,
            @PathVariable Long orderId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying menu to add items to existing order - role: {}, orderId: {}", role, orderId);

        // Validate role
        validateRole(role, authentication);

        // Get logged-in employee
        String username = authentication.getName();
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado para usuario: " + username));

        // Get the order
        OrderService orderService = getOrderService(role);
        Order order = orderService.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

        // Validate order can accept new items (not PAID or CANCELLED)
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No se pueden agregar items a un pedido " + order.getStatus().getDisplayName());
            return "redirect:/" + role + "/orders";
        }

        // Update availability for all items based on current stock
        itemMenuService.updateAllItemsAvailability();
        
        // Get all active categories with their menu items
        List<Category> categories = categoryService.getAllActiveCategories();
        
        // Get available menu items grouped by category
        List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
        
        // Group items by category for easier display
        Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
            .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));

        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Get enabled payment methods from configuration
        Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
        List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(PaymentMethodType::name))
                .collect(Collectors.toList());

        // Set model attributes - similar to new order but with existing order context
        model.addAttribute("orderType", order.getOrderType());
        model.addAttribute("selectedTable", order.getTable());
        model.addAttribute("customerName", order.getCustomerName());
        model.addAttribute("customerPhone", order.getCustomerPhone());
        model.addAttribute("deliveryAddress", order.getDeliveryAddress());
        model.addAttribute("deliveryReferences", order.getDeliveryReferences());
        model.addAttribute("categories", categories);
        model.addAttribute("itemsByCategory", itemsByCategory);
        model.addAttribute("allItems", availableItems);
        model.addAttribute("employee", employee);
        model.addAttribute("currentRole", role);
        model.addAttribute("config", config);
        model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
        
        // Add active promotions for items
        List<Promotion> activePromotions = promotionService.findActivePromotions();
        model.addAttribute("activePromotions", activePromotions);
        
        // IMPORTANT: Add existing order ID and number so the template knows it's "add mode"
        model.addAttribute("existingOrderId", order.getIdOrder());
        model.addAttribute("existingOrderNumber", order.getOrderNumber());

        return role + "/orders/order-menu";
    }

    /**
     * Add items to existing order (POST handler)
     * POST /{role}/orders/{orderId}/add-items
     */
    @PostMapping("/{orderId}/add-items")
    public String addItemsToOrder(
            @PathVariable String role,
            @PathVariable Long orderId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        log.info("Adding items to existing order ID: {} by user: {} (role: {})", orderId, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);
        
        log.info("Item IDs: {}", itemIds);
        log.info("Quantities: {}", quantities);
        log.info("Comments: {}", comments);
        log.info("Promotion Prices: {}", promotionPrices);
        log.info("Promotion IDs: {}", promotionIds);

        try {
            // Get the existing order WITH details (important for recalculation)
            Order order = orderService.findByIdWithDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order can accept new items
            if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No se pueden agregar items a un pedido " + order.getStatus().getDisplayName());
                return "redirect:/" + role + "/orders";
            }

            // Build order details from form data
            List<OrderDetail> newOrderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds);

            if (newOrderDetails.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Debe agregar al menos un item al pedido");
                return "redirect:/" + role + "/orders/" + orderId + "/add-items";
            }

            log.info("Built {} new order details", newOrderDetails.size());
            log.info("Order before adding items - Details count: {}, Subtotal: {}, Total: {}", 
                order.getOrderDetails().size(), order.getSubtotal(), order.getTotal());

            // Add new items to order
            for (OrderDetail detail : newOrderDetails) {
                detail.setOrder(order);
                order.getOrderDetails().add(detail);
            }

            log.info("Order after adding items - Details count: {}", order.getOrderDetails().size());

            // Recalculate all amounts (subtotal, tax, total) with new items
            order.recalculateAmounts();

            log.info("Order after recalculation - Subtotal: {}, Tax: {}, Total: {}", 
                order.getSubtotal(), order.getTaxAmount(), order.getTotal());

            // Update order status based on item statuses
            // This will keep the order in the correct state based on whether items require preparation
            order.updateStatusFromItems();

            log.info("Order status after adding items: {}", order.getStatus());

            // Update audit fields
            order.setUpdatedBy(username);
            order.setUpdatedAt(LocalDateTime.now());

            // Deduct stock for new items
            for (OrderDetail detail : newOrderDetails) {
                ItemMenu item = detail.getItemMenu();
                // Deduct stock from ingredients
                for (ItemIngredient itemIngredient : item.getIngredients()) {
                    Ingredient ingredient = itemIngredient.getIngredient();
                    BigDecimal quantityNeeded = itemIngredient.getQuantity()
                        .multiply(BigDecimal.valueOf(detail.getQuantity()));
                    
                    BigDecimal newStock = ingredient.getCurrentStock().subtract(quantityNeeded);
                    ingredient.setCurrentStock(newStock);
                    ingredient.setUpdatedAt(LocalDateTime.now());
                }
            }

            // Save order directly (bypass update method to avoid stock return logic)
            Order updated = orderRepository.save(order);

            log.info("Items added successfully to order: {}", updated.getOrderNumber());
            
            // Send WebSocket notification for items added to existing order
            try {
                wsNotificationService.notifyItemsAdded(updated, newOrderDetails.size());
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification for items added to order: {}", 
                    updated.getOrderNumber(), e);
            }
            
            redirectAttributes.addFlashAttribute("successMessage",
                    "Se agregaron " + newOrderDetails.size() + " items al pedido " + updated.getOrderNumber());
            
            return "redirect:/" + role + "/orders/view/" + orderId;

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error adding items to order: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/" + role + "/orders/" + orderId + "/add-items";

        } catch (Exception e) {
            log.error("Error adding items to order", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al agregar items al pedido: " + e.getMessage());
            return "redirect:/" + role + "/orders/" + orderId + "/add-items";
        }
    }

    /**
     * Show form to create a new order
     */
    @GetMapping("/new")
    public String newOrderForm(
            @PathVariable String role,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String orderType,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying new order form - role: {}", role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        // Get logged-in employee
        String username = authentication.getName();
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado para usuario: " + username));

        Order order = new Order();
        order.setEmployee(employee);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedBy(username);

        // Set table if provided
        if (tableId != null) {
            RestaurantTable table = restaurantTableService.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
            
            // Validate table availability
            if (!orderService.isTableAvailableForOrder(tableId)) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "La mesa #" + table.getTableNumber() + " no está disponible o ya tiene un pedido activo");
                return "redirect:/" + role + "/orders";
            }
            
            order.setTable(table);
            order.setOrderType(OrderType.DINE_IN);
        }

        // Set order type if provided
        if (orderType != null) {
            try {
                order.setOrderType(OrderType.valueOf(orderType));
            } catch (IllegalArgumentException e) {
                order.setOrderType(OrderType.DINE_IN);
            }
        } else if (order.getOrderType() == null) {
            order.setOrderType(OrderType.DINE_IN);
        }

        // Get system configuration for tax rate and payment methods
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Get available tables (AVAILABLE) and include RESERVED tables that can be occupied now
        List<RestaurantTable> availableTables = new ArrayList<>(restaurantTableService.findAvailableTables());
        // Add reserved tables that pass the reservation occupation validation
        List<RestaurantTable> reservedTables = restaurantTableService.findByStatus(com.aatechsolutions.elgransazon.domain.entity.TableStatus.RESERVED);
        for (RestaurantTable t : reservedTables) {
            try {
                if (restaurantTableService.canBeOccupiedNow(t.getId())) {
                    // avoid duplicates
                    if (availableTables.stream().noneMatch(x -> x.getId().equals(t.getId()))) {
                        availableTables.add(t);
                    }
                }
            } catch (Exception ignored) {
                // ignore any validation exceptions here - table will not be added
            }
        }
        
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

        // Get enabled payment methods
        Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
        List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        model.addAttribute("order", order);
        model.addAttribute("employee", employee);
        model.addAttribute("orderDetails", new ArrayList<>()); // Empty list for new orders
        model.addAttribute("availableTables", availableTables);
        model.addAttribute("availableItems", availableItemsDTO);
        model.addAttribute("orderTypes", OrderType.values());
        model.addAttribute("paymentMethods", enabledPaymentMethods);
        model.addAttribute("taxRate", config.getTaxRate());
        model.addAttribute("formAction", "/" + role + "/orders");
        model.addAttribute("currentRole", role);

        return role + "/orders/form";
    }

    /**
     * Create a new order
     */
    @PostMapping
    public String createOrder(
            @PathVariable String role,
            @ModelAttribute("order") Order order,  // Sin @Valid - los campos se llenan programáticamente
            BindingResult bindingResult,
            @RequestParam(value = "employeeId", required = true) Long employeeId,
            @RequestParam(value = "tableId", required = false) Long tableId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        log.info("Creating new order by user: {} (role: {})", username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);
        log.info("Employee ID (param): {}", employeeId);
        log.info("Order Type: {}", order.getOrderType());
        log.info("Table ID (param): {}", tableId);
        log.info("Customer Name: {}", order.getCustomerName());
        log.info("Customer Phone: {}", order.getCustomerPhone());
        log.info("Delivery Address: {}", order.getDeliveryAddress());
        log.info("Payment Method: {}", order.getPaymentMethod());
        log.info("Item IDs: {}", itemIds);
        log.info("Quantities: {}", quantities);
        log.info("Comments: {}", comments);

        // No validamos bindingResult porque Order se completa programáticamente

        try {
            // Validate payment method is enabled
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            if (!config.isPaymentMethodEnabled(order.getPaymentMethod())) {
                log.warn("Payment method not enabled: {}", order.getPaymentMethod());
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "El método de pago seleccionado no está habilitado: " + order.getPaymentMethod().getDisplayName());
                return "redirect:/" + role + "/orders/menu?orderType=" + order.getOrderType().name() +
                    (tableId != null ? "&tableId=" + tableId : "") +
                    (order.getCustomerName() != null ? "&customerName=" + order.getCustomerName() : "") +
                    (order.getCustomerPhone() != null ? "&customerPhone=" + order.getCustomerPhone() : "");
            }
            
            // Set employee
            Employee employee = employeeService.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + employeeId));
            order.setEmployee(employee);
            
            // Set table if provided
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada con ID: " + tableId));
                order.setTable(table);
            }
            
            // Build order details from form data
            List<OrderDetail> orderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds);

            if (orderDetails.isEmpty()) {
                model.addAttribute("errorMessage", "Debe agregar al menos un item al pedido");
                loadFormData(model, order, username, role);
                return role + "/orders/form";
            }

            // Set audit fields
            order.setCreatedBy(username);

            // Create order
            Order created = orderService.create(order, orderDetails);

            log.info("Order created successfully: {}", created.getOrderNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido " + created.getOrderNumber() + " creado exitosamente");
            return "redirect:/" + role + "/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error creating order: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            loadFormData(model, order, username, role);
            return role + "/orders/form";

        } catch (Exception e) {
            log.error("Error creating order", e);
            model.addAttribute("errorMessage", "Error al crear el pedido: " + e.getMessage());
            loadFormData(model, order, username, role);
            return role + "/orders/form";
        }
    }

    /**
     * Show form to edit an existing order (only PENDING orders)
     */
    @GetMapping("/edit/{id}")
    public String editOrderForm(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying edit form for order ID: {} (role: {})", id, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        return orderService.findByIdWithDetails(id)
                .map(order -> {
                    // Cannot edit PAID or CANCELLED orders
                    if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELLED) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "No se pueden editar pedidos PAGADOS o CANCELADOS");
                        return "redirect:/" + role + "/orders";
                    }

                    SystemConfiguration config = systemConfigurationService.getConfiguration();
                    
                    List<RestaurantTable> availableTables = new ArrayList<>(restaurantTableService.findAvailableTables());
                    List<RestaurantTable> reservedTables = restaurantTableService.findByStatus(com.aatechsolutions.elgransazon.domain.entity.TableStatus.RESERVED);
                    for (RestaurantTable t : reservedTables) {
                        try {
                            if (restaurantTableService.canBeOccupiedNow(t.getId())) {
                                if (availableTables.stream().noneMatch(x -> x.getId().equals(t.getId()))) {
                                    availableTables.add(t);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    
                    // If order has a table assigned, ensure it's in the list
                    if (order.getTable() != null) {
                        boolean tableInList = availableTables.stream()
                            .anyMatch(t -> t.getId().equals(order.getTable().getId()));
                        if (!tableInList) {
                            availableTables.add(order.getTable());
                        }
                    }
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
                    
                    Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
                    List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                    // Convert order details to simple DTOs to avoid circular reference issues
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
                    model.addAttribute("employee", order.getEmployee()); // Add employee to model
                    model.addAttribute("orderDetails", orderDetailsDTO);
                    model.addAttribute("availableTables", availableTables);
                    model.addAttribute("availableItems", availableItemsDTO);
                    model.addAttribute("orderTypes", OrderType.values());
                    model.addAttribute("paymentMethods", enabledPaymentMethods);
                    model.addAttribute("taxRate", config.getTaxRate());
                    model.addAttribute("formAction", "/" + role + "/orders/" + id);
                    model.addAttribute("currentRole", role);
                    
                    return role + "/orders/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Pedido no encontrado");
                    return "redirect:/" + role + "/orders";
                });
    }

    /**
     * Update an existing order
     */
    @PostMapping("/{id}")
    public String updateOrder(
            @PathVariable String role,
            @PathVariable Long id,
            @ModelAttribute("order") Order order,  // Removed @Valid to handle custom validation
            BindingResult bindingResult,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "tableId", required = false) Long tableId,
            @RequestParam(value = "itemIds", required = false) List<Long> itemIds,
            @RequestParam(value = "quantities", required = false) List<Integer> quantities,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "promotionPrices", required = false) List<String> promotionPrices,
            @RequestParam(value = "promotionIds", required = false) List<String> promotionIds,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        log.info("Updating order with ID: {} by user: {} (role: {})", id, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        try {
            // Build order details from form data
            List<OrderDetail> orderDetails = buildOrderDetails(itemIds, quantities, comments, promotionPrices, promotionIds);

            if (orderDetails.isEmpty()) {
                model.addAttribute("errorMessage", "Debe agregar al menos un item al pedido");
                loadFormData(model, order, username, role);
                model.addAttribute("formAction", "/" + role + "/orders/" + id);
                return role + "/orders/form";
            }

            // Set table from form data
            if (tableId != null) {
                RestaurantTable table = restaurantTableService.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));
                order.setTable(table);
                log.info("Table #{} will be assigned to order", table.getTableNumber());
            } else {
                order.setTable(null);
                log.info("No table will be assigned to order");
            }

            // Set audit fields
            order.setUpdatedBy(username);

            // Update order - the service will handle the table changes
            Order updated = orderService.update(id, order, orderDetails);

            log.info("Order updated successfully: {}", updated.getOrderNumber());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pedido " + updated.getOrderNumber() + " actualizado exitosamente");
            return "redirect:/" + role + "/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error updating order: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            loadFormData(model, order, username, role);
            model.addAttribute("formAction", "/" + role + "/orders/" + id);
            return role + "/orders/form";

        } catch (Exception e) {
            log.error("Error updating order", e);
            model.addAttribute("errorMessage", "Error al actualizar el pedido: " + e.getMessage());
            loadFormData(model, order, username, role);
            model.addAttribute("formAction", "/" + role + "/orders/" + id);
            return role + "/orders/form";
        }
    }

    /**
     * View order details
     */
    @GetMapping("/view/{id}")
    public String viewOrder(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        log.debug("Viewing order ID: {} (role: {})", id, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        return orderService.findById(id)
                .map(order -> {
                    model.addAttribute("order", order);
                    model.addAttribute("orderDetails", order.getOrderDetails());
                    model.addAttribute("currentRole", role);
                    return role + "/orders/view";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Pedido no encontrado");
                    return "redirect:/" + role + "/orders";
                });
    }

    /**
     * Cancel an order (AJAX)
     */
    @PostMapping("/{id}/cancel")
    @ResponseBody
    public Map<String, Object> cancelOrder(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Cancelling order ID: {} by user: {} (role: {})", id, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            Order cancelled = orderService.cancel(id, username);
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
     * Change order status (AJAX)
     */
    @PostMapping("/{id}/change-status")
    @ResponseBody
    public Map<String, Object> changeStatus(
            @PathVariable String role,
            @PathVariable Long id,
            @RequestParam String newStatus,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Changing order status. ID: {}, New Status: {}, User: {} (role: {})", id, newStatus, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderStatus status = OrderStatus.valueOf(newStatus);
            
            // Get current employee
            Employee currentEmployee = employeeService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));
            
            // Get the order to check current state
            Order order = orderService.findByIdOrThrow(id);
            
            // Validate payment method restrictions for waiters
            if (status == OrderStatus.PAID && 
                "waiter".equalsIgnoreCase(role) && 
                order.getPaymentMethod() == PaymentMethodType.CASH) {
                response.put("success", false);
                response.put("message", "Los meseros no pueden cobrar órdenes en efectivo. Solo el cajero puede hacerlo.");
                return response;
            }
            
            // Set preparedBy BEFORE changing status (when someone accepts the order)
            if (status == OrderStatus.IN_PREPARATION && order.getStatus() == OrderStatus.PENDING && order.getPreparedBy() == null) {
                // Update the preparedBy field directly in the repository
                order.setPreparedBy(currentEmployee);
                orderRepository.save(order);
                log.info("Setting preparedBy to: {} (role: {}) for order {}", username, role, id);
            }
            
            // Set paidBy BEFORE changing status (when order is marked as PAID)
            if (status == OrderStatus.PAID && order.getPaidBy() == null) {
                // Update the paidBy field directly in the repository
                order.setPaidBy(currentEmployee);
                orderRepository.save(order);
                log.info("Setting paidBy to: {} (role: {}) for order {}", username, role, id);
            }
            
            // Now change the status
            Order updated = orderService.changeStatus(id, status, username);
            
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
     * Add new items to an existing order (AJAX)
     * Only available for DINE_IN orders (customers at table can order more)
     */
    @PostMapping("/{id}/add-items-ajax")
    @ResponseBody
    public Map<String, Object> addItemsToOrderAjax(
            @PathVariable String role,
            @PathVariable Long id,
            @RequestBody List<AddItemRequest> items,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Adding {} items to order ID: {} by user: {} (role: {})", 
                 items.size(), id, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get the existing order
            Order order = orderService.findByIdOrThrow(id);

            // Validate that order can accept new items
            if (!order.canAcceptNewItems()) {
                response.put("success", false);
                response.put("message", "No se pueden agregar items a este pedido. " +
                    "Solo pedidos para COMER AQUÍ pueden recibir items adicionales.");
                return response;
            }

            // Build new order details
            List<OrderDetail> newItems = new ArrayList<>();
            for (AddItemRequest itemRequest : items) {
                ItemMenu item = itemMenuService.findById(itemRequest.getIdItemMenu())
                    .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemRequest.getIdItemMenu()));

                OrderDetail detail = OrderDetail.builder()
                    .itemMenu(item)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(item.getPrice())
                    .comments(itemRequest.getComments())
                    .build();

                detail.calculateSubtotal();
                newItems.add(detail);
            }

            // Add items to order
            Order updated = orderService.addItemsToExistingOrder(id, newItems, username);

            response.put("success", true);
            response.put("message", String.format("Se agregaron %d items al pedido. " +
                "Los nuevos items aparecerán en cocina como PENDIENTES.", newItems.size()));
            response.put("order", buildOrderDTO(updated));
            response.put("newItemsCount", newItems.size());
            response.put("newTotal", updated.getFormattedTotal());
        } catch (IllegalStateException e) {
            log.error("Error adding items to order: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error adding items to order", e);
            response.put("success", false);
            response.put("message", "Error al agregar items: " + e.getMessage());
        }

        return response;
    }

    /**
     * Change status of specific items in an order (AJAX)
     * Used by chef to update individual item statuses
     */
    @PostMapping("/{id}/change-items-status")
    @ResponseBody
    public Map<String, Object> changeItemsStatus(
            @PathVariable String role,
            @PathVariable Long id,
            @RequestParam List<Long> itemDetailIds,
            @RequestParam String newStatus,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Changing status of {} items in order {} to {} by user: {} (role: {})", 
                 itemDetailIds.size(), id, newStatus, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderStatus status = OrderStatus.valueOf(newStatus);
            
            Order updated = orderService.changeItemsStatus(id, itemDetailIds, status, username);

            response.put("success", true);
            response.put("message", String.format("Se cambió el estado de %d items a %s", 
                itemDetailIds.size(), status.getDisplayName()));
            response.put("order", buildOrderDTO(updated));
            response.put("orderStatus", updated.getStatus().name());
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", newStatus);
            response.put("success", false);
            response.put("message", "Estado inválido: " + newStatus);
        } catch (IllegalStateException e) {
            log.error("Error changing items status: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error changing items status", e);
            response.put("success", false);
            response.put("message", "Error al cambiar el estado de los items: " + e.getMessage());
        }

        return response;
    }

    /**
     * Delete a specific item from an order (AJAX)
     */
    @DeleteMapping("/{orderId}/items/{itemId}")
    @ResponseBody
    public Map<String, Object> deleteOrderItem(
            @PathVariable String role,
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            Authentication authentication) {
        
        String username = authentication.getName();
        log.info("Deleting item {} from order {} by user: {} (role: {})", itemId, orderId, username, role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        Map<String, Object> response = new HashMap<>();
        
        try {
            OrderDetail deletedItem = orderService.deleteOrderItem(orderId, itemId, username);
            
            // Get updated order
            Order order = orderService.findByIdOrThrow(orderId);
            
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
     * Get valid next statuses for an order (AJAX)
     */
    @GetMapping("/{id}/valid-statuses")
    @ResponseBody
    public Map<String, Object> getValidStatuses(
            @PathVariable String role,
            @PathVariable Long id,
            Authentication authentication) {
        
        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Order order = orderService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
            
            List<OrderStatus> validStatuses = new ArrayList<>();
            
            // Different rules based on role
            if ("chef".equalsIgnoreCase(role)) {
                // Chef can only change: PENDING -> IN_PREPARATION, IN_PREPARATION -> READY
                if (order.getStatus() == OrderStatus.PENDING) {
                    validStatuses.add(OrderStatus.IN_PREPARATION);
                } else if (order.getStatus() == OrderStatus.IN_PREPARATION) {
                    validStatuses.add(OrderStatus.READY);
                }
            } else if ("waiter".equalsIgnoreCase(role)) {
                // Waiter can only mark as DELIVERED if status is READY
                if (order.getStatus() == OrderStatus.READY) {
                    validStatuses.add(OrderStatus.DELIVERED);
                }
                // Waiter can mark as PAID if status is DELIVERED and payment method is not CASH
                if (order.getStatus() == OrderStatus.DELIVERED && 
                    order.getPaymentMethod() != PaymentMethodType.CASH) {
                    validStatuses.add(OrderStatus.PAID);
                }
            } else {
                // Admin has full access - use default behavior
                OrderStatus[] allValidStatuses = OrderStatus.getValidNextStatuses(
                    order.getStatus(), 
                    order.getOrderType()
                );
                validStatuses = Arrays.asList(allValidStatuses);
            }
            
            List<Map<String, String>> statusList = new ArrayList<>();
            for (OrderStatus status : validStatuses) {
                Map<String, String> statusMap = new HashMap<>();
                statusMap.put("value", status.name());
                statusMap.put("label", status.getDisplayName());
                statusList.add(statusMap);
            }
            
            response.put("success", true);
            response.put("currentStatus", order.getStatus().name());
            response.put("currentStatusLabel", order.getStatus().getDisplayName());
            response.put("orderType", order.getOrderType().name());
            response.put("validStatuses", statusList);
            response.put("canBeCancelled", order.getStatus().canBeCancelled());
        } catch (Exception e) {
            log.error("Error getting valid statuses", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return response;
    }

    // ========== AJAX ENDPOINTS ==========

    /**
     * Validate stock for order items (AJAX)
     */
    @PostMapping("/validate-stock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateStock(
            @PathVariable String role,
            @RequestBody List<OrderDetailRequest> items,
            Authentication authentication) {
        
        log.debug("Validating stock for {} items (role: {})", items.size(), role);

        // Validate role
        validateRole(role, authentication);
        
        // Get the correct service based on role
        OrderService orderService = getOrderService(role);

        try {
            List<OrderDetail> orderDetails = items.stream()
                .map(req -> {
                    ItemMenu item = itemMenuService.findById(req.getItemId())
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado"));
                    
                    OrderDetail detail = new OrderDetail();
                    detail.setItemMenu(item);
                    detail.setQuantity(req.getQuantity());
                    return detail;
                })
                .collect(Collectors.toList());

            Map<Long, String> stockErrors = orderService.validateStock(orderDetails);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", stockErrors.isEmpty());
            response.put("errors", stockErrors);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error validating stock", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("valid", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Calculate order total (AJAX)
     */
    @PostMapping("/calculate-total")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> calculateTotal(
            @RequestBody List<OrderDetailRequest> items) {
        
        log.debug("Calculating total for {} items", items.size());

        try {
            BigDecimal subtotal = BigDecimal.ZERO;

            for (OrderDetailRequest req : items) {
                ItemMenu item = itemMenuService.findById(req.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item no encontrado"));
                
                BigDecimal itemSubtotal = item.getPrice()
                    .multiply(BigDecimal.valueOf(req.getQuantity()));
                subtotal = subtotal.add(itemSubtotal);
            }

            SystemConfiguration config = systemConfigurationService.getConfiguration();
            BigDecimal taxRate = config.getTaxRate();
            BigDecimal taxAmount = subtotal.multiply(taxRate).divide(BigDecimal.valueOf(100));
            BigDecimal total = subtotal.add(taxAmount);

            Map<String, Object> response = new HashMap<>();
            response.put("subtotal", subtotal);
            response.put("taxRate", taxRate);
            response.put("taxAmount", taxAmount);
            response.put("total", total);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error calculating total", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get available items with stock info (AJAX)
     */
    @GetMapping("/available-items")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableItems() {
        log.debug("Getting available items");

        try {
            List<ItemMenu> items = itemMenuService.findAvailableItems();
            
            List<Map<String, Object>> response = items.stream()
                .map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getIdItemMenu());
                    itemMap.put("name", item.getName());
                    itemMap.put("price", item.getPrice());
                    itemMap.put("available", item.getAvailable());
                    itemMap.put("categoryName", item.getCategory().getName());
                    return itemMap;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting available items", e);
            return ResponseEntity.badRequest().build();
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

        if (itemIds == null || itemIds.isEmpty()) {
            return orderDetails;
        }

        for (int i = 0; i < itemIds.size(); i++) {
            Long itemId = itemIds.get(i);
            Integer quantity = (quantities != null && i < quantities.size()) ? quantities.get(i) : 1;
            String comment = (comments != null && i < comments.size()) ? comments.get(i) : null;
            String promotionPriceStr = (promotionPrices != null && i < promotionPrices.size()) ? promotionPrices.get(i) : null;
            String promotionIdStr = (promotionIds != null && i < promotionIds.size()) ? promotionIds.get(i) : null;

            if (itemId == null || quantity == null || quantity <= 0) {
                continue;
            }

            ItemMenu item = itemMenuService.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));

            OrderDetail.OrderDetailBuilder detailBuilder = OrderDetail.builder()
                .itemMenu(item)
                .quantity(quantity)
                .unitPrice(item.getPrice())
                .comments(comment);
            
            // Set item status based on whether it requires preparation
            // If item doesn't require preparation, it goes directly to READY status
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
                            
                            log.info("BACKEND VALIDATION - Item: {}, Qty: {}, Promotion: {}, " +
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

        return orderDetails;
    }

    /**
     * Load form data for rendering
     */
    private void loadFormData(Model model, Order order, String username, String role) {
        Employee employee = employeeService.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("Empleado no encontrado"));

        SystemConfiguration config = systemConfigurationService.getConfiguration();
        List<RestaurantTable> availableTables = new ArrayList<>(restaurantTableService.findAvailableTables());
        List<RestaurantTable> reservedTables = restaurantTableService.findByStatus(com.aatechsolutions.elgransazon.domain.entity.TableStatus.RESERVED);
        for (RestaurantTable t : reservedTables) {
            try {
                if (restaurantTableService.canBeOccupiedNow(t.getId())) {
                    if (availableTables.stream().noneMatch(x -> x.getId().equals(t.getId()))) {
                        availableTables.add(t);
                    }
                }
            } catch (Exception ignored) {
            }
        }
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
        
        Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
        List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        model.addAttribute("employee", employee);
        model.addAttribute("availableTables", availableTables);
        model.addAttribute("availableItems", availableItemsDTO);
        model.addAttribute("orderTypes", OrderType.values());
        model.addAttribute("paymentMethods", enabledPaymentMethods);
        model.addAttribute("taxRate", config.getTaxRate());
        model.addAttribute("currentRole", role);
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
        
        // Item-level status information
        dto.put("pendingItemsCount", order.getPendingItemsCount());
        dto.put("newItemsCount", order.getNewItemsCount());
        dto.put("hasPendingItems", order.hasPendingItems());
        dto.put("hasNewItems", order.hasNewItems());
        dto.put("canAcceptNewItems", order.canAcceptNewItems());
        
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
        
        // Include order details with item status
        if (order.getOrderDetails() != null && !order.getOrderDetails().isEmpty()) {
            List<Map<String, Object>> items = order.getOrderDetails().stream()
                .map(this::buildOrderDetailDTO)
                .collect(Collectors.toList());
            dto.put("items", items);
        }
        
        return dto;
    }

    /**
     * Build a DTO for an order detail (item)
     */
    private Map<String, Object> buildOrderDetailDTO(OrderDetail detail) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", detail.getIdOrderDetail());
        dto.put("itemName", detail.getItemMenu().getName());
        dto.put("quantity", detail.getQuantity());
        dto.put("unitPrice", detail.getUnitPrice());
        dto.put("subtotal", detail.getSubtotal());
        dto.put("comments", detail.getComments());
        dto.put("itemStatus", detail.getItemStatus().name());
        dto.put("itemStatusLabel", detail.getItemStatus().getDisplayName());
        dto.put("isNew", detail.isNew());
        dto.put("isPending", detail.isPending());
        dto.put("isInPreparation", detail.isInPreparation());
        dto.put("isReady", detail.isReady());
        dto.put("isDelivered", detail.isDelivered());
        
        if (detail.getPreparedBy() != null) {
            dto.put("preparedBy", detail.getPreparedBy());
        }
        
        return dto;
    }

    /**
     * Get available menu items (AJAX endpoint for add items modal)
     * Returns all available items with their details
     */
    @GetMapping("/menu-items/available")
    @ResponseBody
    public Map<String, Object> getAvailableMenuItems(@PathVariable String role, Authentication authentication) {
        log.info("Fetching available menu items for role: {}", role);
        
        // Validate role
        validateRole(role, authentication);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get all available menu items
            List<ItemMenu> availableItems = itemMenuService.findAll().stream()
                .filter(ItemMenu::getAvailable)
                .collect(Collectors.toList());
            
            // Build DTOs
            List<Map<String, Object>> itemDTOs = availableItems.stream()
                .map(item -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("idItemMenu", item.getIdItemMenu());
                    dto.put("name", item.getName());
                    dto.put("description", item.getDescription());
                    dto.put("price", item.getPrice());
                    dto.put("available", item.getAvailable());
                    dto.put("category", item.getCategory() != null ? item.getCategory().getName() : null);
                    dto.put("categoryId", item.getCategory() != null ? item.getCategory().getIdCategory() : null);
                    return dto;
                })
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("data", itemDTOs);
            response.put("count", itemDTOs.size());
        } catch (Exception e) {
            log.error("Error fetching available menu items", e);
            response.put("success", false);
            response.put("message", "Error al obtener los items del menú: " + e.getMessage());
        }
        
        return response;
    }

    /**
     * DTO for adding items to existing order (AJAX)
     */
    public static class AddItemRequest {
        private Long idItemMenu;
        private Integer quantity;
        private String comments;

        public Long getIdItemMenu() {
            return idItemMenu;
        }

        public void setIdItemMenu(Long idItemMenu) {
            this.idItemMenu = idItemMenu;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getComments() {
            return comments;
        }

        public void setComments(String comments) {
            this.comments = comments;
        }
    }

    /**
     * DTO for order detail request (AJAX)
     */
    public static class OrderDetailRequest {
        private Long itemId;
        private Integer quantity;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
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
    
    /**
     * Get order statistics - REST endpoint for real-time updates
     */
    @GetMapping("/stats")
    @ResponseBody
    public Map<String, Object> getOrderStats(@PathVariable String role, Authentication authentication) {
        validateRole(role, authentication);
        OrderService orderService = getOrderService(role);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayCount", orderService.countTodaysOrders());
        stats.put("todayRevenue", orderService.getTodaysRevenue());
        stats.put("pendingCount", orderService.countByStatus(OrderStatus.PENDING));
        stats.put("inPreparationCount", orderService.countByStatus(OrderStatus.IN_PREPARATION));
        stats.put("activeCount", orderService.findActiveOrders().size());
        
        return stats;
    }
}




