package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.presentation.dto.ChangePasswordDTO;
import com.aatechsolutions.elgransazon.presentation.dto.UpdateProfileDTO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Customer views
 * Handles menu display and order management for customers
 */
@Controller
@RequestMapping("/client")
@PreAuthorize("hasRole('ROLE_CLIENT')")
@Slf4j
public class ClientController {

    private final OrderService orderService;
    private final ItemMenuService itemMenuService;
    private final CategoryService categoryService;
    private final SystemConfigurationService systemConfigurationService;
    private final CustomerService customerService;
    private final PromotionService promotionService;
    private final ReviewService reviewService;
    private final PasswordEncoder passwordEncoder;

    public ClientController(
            @Qualifier("customerOrderService") OrderService orderService,
            ItemMenuService itemMenuService,
            CategoryService categoryService,
            SystemConfigurationService systemConfigurationService,
            CustomerService customerService,
            PromotionService promotionService,
            ReviewService reviewService,
            PasswordEncoder passwordEncoder) {
        this.orderService = orderService;
        this.itemMenuService = itemMenuService;
        this.categoryService = categoryService;
        this.systemConfigurationService = systemConfigurationService;
        this.customerService = customerService;
        this.promotionService = promotionService;
        this.reviewService = reviewService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Show customer dashboard (landing page after login)
     */
    @GetMapping("/dashboard")
    public String showDashboard(Authentication authentication, Model model) {
        log.debug("Customer {} accessing dashboard", authentication.getName());
        
        try {
            // Get customer info
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Get customer statistics
            List<Order> allOrders = orderService.findAll();
            long totalOrders = allOrders.size();
            long activeOrders = allOrders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED && 
                               o.getStatus() != OrderStatus.PAID)
                    .count();
            
            model.addAttribute("customer", customer);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("activeOrders", activeOrders);
            
            return "client/dashboard";
            
        } catch (Exception e) {
            log.error("Error loading dashboard for customer", e);
            model.addAttribute("errorMessage", "Error al cargar el dashboard: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show menu to customer (landing page after login)
     */
    @GetMapping("/menu")
    public String showMenu(Authentication authentication, Model model) {
        log.debug("Customer {} accessing menu", authentication.getName());
        
        try {
            // Update item availability
            itemMenuService.updateAllItemsAvailability();
            
            // Get active categories and available items
            List<Category> categories = categoryService.getAllActiveCategories();
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            // Get system configuration
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            
            // Get customer info
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Get enabled payment methods
            Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
            List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            model.addAttribute("currentRole", "client");
            model.addAttribute("customer", customer);
            model.addAttribute("orderTypes", Arrays.asList(OrderType.TAKEOUT, OrderType.DELIVERY));
            model.addAttribute("orderType", OrderType.TAKEOUT); // Default order type
            model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
            
            return "client/menu";
            
        } catch (Exception e) {
            log.error("Error loading menu for customer", e);
            model.addAttribute("errorMessage", "Error al cargar el menú: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show order history for customer
     */
    @GetMapping("/orders")
    public String showOrderHistory(Authentication authentication, Model model) {
        log.debug("Customer {} accessing order history", authentication.getName());
        
        try {
            // Get customer orders
            List<Order> orders = orderService.findAll();
            
            // Sort by created date descending
            orders.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
            
            // Calculate statistics
            long totalOrders = orders.size();
            long activeOrders = orders.stream()
                    .filter(o -> o.getStatus() != OrderStatus.CANCELLED && 
                               o.getStatus() != OrderStatus.PAID)
                    .count();
            long completedOrders = orders.stream()
                    .filter(o -> o.getStatus() == OrderStatus.PAID)
                    .count();
            
            model.addAttribute("orders", orders);
            model.addAttribute("totalOrders", totalOrders);
            model.addAttribute("activeOrders", activeOrders);
            model.addAttribute("completedOrders", completedOrders);
            model.addAttribute("orderStatuses", OrderStatus.values());
            
            return "client/orders";
            
        } catch (Exception e) {
            log.error("Error loading order history for customer", e);
            model.addAttribute("errorMessage", "Error al cargar el historial: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show order details
     */
    @GetMapping("/orders/{id}")
    public String showOrderDetail(@PathVariable Long id, Authentication authentication, Model model,
                                   RedirectAttributes redirectAttributes) {
        log.debug("Customer {} accessing order detail: {}", authentication.getName(), id);
        
        try {
            Order order = orderService.findByIdWithDetails(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
            
            model.addAttribute("order", order);
            model.addAttribute("orderDetails", order.getOrderDetails());
            
            return "client/order-detail";
            
        } catch (Exception e) {
            log.error("Error loading order detail", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/client/orders";
        }
    }

    /**
     * Create new order (AJAX endpoint)
     */
    @PostMapping("/orders/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> orderData,
            Authentication authentication) {
        
        log.info("Customer {} creating new order", authentication.getName());
        
        try {
            // Get customer
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Parse order data
            OrderType orderType = OrderType.valueOf((String) orderData.get("orderType"));
            PaymentMethodType paymentMethod = PaymentMethodType.valueOf((String) orderData.get("paymentMethod"));
            
            // For DELIVERY orders, validate customer has address
            String deliveryAddress = null;
            String deliveryReferences = null;
            
            if (orderType == OrderType.DELIVERY) {
                // Use customer's registered address
                if (customer.getAddress() == null || customer.getAddress().trim().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Para pedidos a domicilio debes tener una dirección registrada en tu perfil"
                    ));
                }
                deliveryAddress = customer.getAddress();
                deliveryReferences = (String) orderData.get("deliveryReferences");
            }
            
            BigDecimal taxRate = new BigDecimal(systemConfigurationService.getConfiguration().getTaxRate().toString());
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) orderData.get("items");
            
            // Create order
            Order order = Order.builder()
                    .orderType(orderType)
                    .paymentMethod(paymentMethod)
                    .deliveryAddress(deliveryAddress)
                    .deliveryReferences(deliveryReferences)
                    .taxRate(taxRate)
                    .status(OrderStatus.PENDING)
                    .customer(customer)
                    .customerName(customer.getFullName())
                    .customerPhone(customer.getPhone())
                    .createdBy(authentication.getName())
                    .build();
            
            // Create order details
            List<OrderDetail> orderDetails = new ArrayList<>();
            for (Map<String, Object> itemData : items) {
                Long itemId = Long.valueOf(itemData.get("itemId").toString());
                Integer quantity = Integer.valueOf(itemData.get("quantity").toString());
                String comments = (String) itemData.get("comments");
                
                ItemMenu itemMenu = itemMenuService.findById(itemId)
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));
                
                // Get promotion data from frontend (if user selected one)
                BigDecimal unitPrice = itemMenu.getPrice();
                BigDecimal promotionPrice = null;
                Long promotionId = null;
                
                // Check if promotion was applied in frontend
                Object promotionPriceObj = itemData.get("promotionPrice");
                Object promotionIdObj = itemData.get("promotionId");
                
                if (promotionPriceObj != null && !promotionPriceObj.toString().isEmpty()) {
                    try {
                        promotionPrice = new BigDecimal(promotionPriceObj.toString());
                        log.debug("Using promotion price from frontend: {} for item {}", promotionPrice, itemId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion price format: {}", promotionPriceObj);
                    }
                }
                
                if (promotionIdObj != null && !promotionIdObj.toString().isEmpty()) {
                    try {
                        promotionId = Long.valueOf(promotionIdObj.toString());
                        log.debug("Using promotion ID from frontend: {} for item {}", promotionId, itemId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion ID format: {}", promotionIdObj);
                    }
                }
                
                OrderDetail detail = OrderDetail.builder()
                        .itemMenu(itemMenu)
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .promotionAppliedPrice(promotionPrice)
                        .appliedPromotionId(promotionId)
                        .comments(comments)
                        .itemStatus(OrderStatus.PENDING)
                        .build();
                
                detail.calculateSubtotal();
                orderDetails.add(detail);
            }
            
            // Validate stock
            Map<Long, String> stockErrors = orderService.validateStock(orderDetails);
            if (!stockErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Stock insuficiente para algunos items",
                    "stockErrors", stockErrors
                ));
            }
            
            // Create order
            Order createdOrder = orderService.create(order, orderDetails);
            
            log.info("Order created successfully: {}", createdOrder.getOrderNumber());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Pedido creado exitosamente",
                "orderNumber", createdOrder.getOrderNumber(),
                "orderId", createdOrder.getIdOrder()
            ));
            
        } catch (Exception e) {
            log.error("Error creating order", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error al crear el pedido: " + e.getMessage()
            ));
        }
    }

    /**
     * Show menu to add items to existing order
     * GET /client/orders/{orderId}/add-items
     */
    @GetMapping("/orders/{orderId}/add-items")
    public String showMenuToAddItems(
            @PathVariable Long orderId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Customer {} adding items to order {}", authentication.getName(), orderId);

        try {
            // Get customer
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));

            // Get the order
            Order order = orderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order belongs to customer
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para modificar este pedido");
                return "redirect:/client/orders";
            }

            // Validate order can accept new items
            if (!order.canAcceptNewItems()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s",
                        order.getOrderType().getDisplayName(),
                        order.getStatus().getDisplayName()));
                return "redirect:/client/orders";
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
                    .collect(Collectors.toList());

            // Set model attributes - similar to new order but with existing order context
            model.addAttribute("orderType", order.getOrderType());
            model.addAttribute("customerName", order.getCustomerName());
            model.addAttribute("customerPhone", order.getCustomerPhone());
            model.addAttribute("deliveryAddress", order.getDeliveryAddress());
            model.addAttribute("deliveryReferences", order.getDeliveryReferences());
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            model.addAttribute("allItems", availableItems);
            model.addAttribute("customer", customer);
            model.addAttribute("currentRole", "client");
            model.addAttribute("config", config);
            model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
            
            // Add active promotions for items
            List<Promotion> activePromotions = promotionService.findActivePromotions();
            model.addAttribute("activePromotions", activePromotions);
            
            // IMPORTANT: Add existing order ID and number so the template knows it's "add mode"
            model.addAttribute("existingOrderId", order.getIdOrder());
            model.addAttribute("existingOrderNumber", order.getOrderNumber());

            return "client/add-items-menu";
            
        } catch (Exception e) {
            log.error("Error showing add items menu", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/client/orders";
        }
    }

    /**
     * Add items to existing order (AJAX endpoint)
     * POST /client/orders/{orderId}/add-items
     */
    @PostMapping("/orders/{orderId}/add-items")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addItemsToOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> requestData,
            Authentication authentication) {
        
        log.info("Customer {} adding items to order {}", authentication.getName(), orderId);

        try {
            // Get customer
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));

            // Get the order
            Order order = orderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order belongs to customer
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No tienes permiso para modificar este pedido"
                ));
            }

            // Validate order can accept new items
            if (!order.canAcceptNewItems()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", String.format("No se pueden agregar items a este pedido. Tipo: %s, Estado: %s",
                        order.getOrderType().getDisplayName(),
                        order.getStatus().getDisplayName())
                ));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) requestData.get("items");

            // Create new order details
            List<OrderDetail> newItems = new ArrayList<>();
            for (Map<String, Object> itemData : items) {
                Long itemId = Long.valueOf(itemData.get("itemId").toString());
                Integer quantity = Integer.valueOf(itemData.get("quantity").toString());
                String comments = (String) itemData.get("comments");

                ItemMenu itemMenu = itemMenuService.findById(itemId)
                        .orElseThrow(() -> new IllegalArgumentException("Item no encontrado: " + itemId));

                // Get promotion data from frontend (if user selected one)
                BigDecimal unitPrice = itemMenu.getPrice();
                BigDecimal promotionPrice = null;
                Long promotionId = null;

                // Check if promotion was applied in frontend
                Object promotionPriceObj = itemData.get("promotionPrice");
                Object promotionIdObj = itemData.get("promotionId");

                if (promotionPriceObj != null && !promotionPriceObj.toString().isEmpty()) {
                    try {
                        promotionPrice = new BigDecimal(promotionPriceObj.toString());
                        log.debug("Using promotion price from frontend: {} for item {}", promotionPrice, itemId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion price format: {}", promotionPriceObj);
                    }
                }

                if (promotionIdObj != null && !promotionIdObj.toString().isEmpty()) {
                    try {
                        promotionId = Long.valueOf(promotionIdObj.toString());
                        log.debug("Using promotion ID from frontend: {} for item {}", promotionId, itemId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid promotion ID format: {}", promotionIdObj);
                    }
                }

                OrderDetail detail = OrderDetail.builder()
                        .itemMenu(itemMenu)
                        .quantity(quantity)
                        .unitPrice(unitPrice)
                        .promotionAppliedPrice(promotionPrice)
                        .appliedPromotionId(promotionId)
                        .comments(comments)
                        .itemStatus(OrderStatus.PENDING)
                        .build();

                detail.calculateSubtotal();
                newItems.add(detail);
            }

            // Add items to order
            Order updatedOrder = orderService.addItemsToExistingOrder(orderId, newItems, authentication.getName());

            log.info("Items added successfully to order: {}", updatedOrder.getOrderNumber());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Items agregados exitosamente al pedido",
                "orderNumber", updatedOrder.getOrderNumber(),
                "orderId", updatedOrder.getIdOrder()
            ));

        } catch (Exception e) {
            log.error("Error adding items to order", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error al agregar items: " + e.getMessage()
            ));
        }
    }

    /**
     * Cancel order (AJAX endpoint)
     * POST /client/orders/{orderId}/cancel
     */
    @PostMapping("/orders/{orderId}/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable Long orderId,
            Authentication authentication) {
        
        log.info("Customer {} cancelling order {}", authentication.getName(), orderId);

        try {
            // Get customer
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));

            // Get the order
            Order order = orderService.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));

            // Validate order belongs to customer
            if (!order.getCustomer().getIdCustomer().equals(customer.getIdCustomer())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No tienes permiso para cancelar este pedido"
                ));
            }

            // Validate order is in PENDING status
            if (order.getStatus() != OrderStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Solo se pueden cancelar pedidos en estado PENDIENTE. Estado actual: " + order.getStatus().getDisplayName()
                ));
            }

            // Cancel the order
            Order cancelledOrder = orderService.cancel(orderId, authentication.getName());

            log.info("Order {} cancelled successfully by customer {}", cancelledOrder.getOrderNumber(), authentication.getName());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Pedido " + cancelledOrder.getOrderNumber() + " cancelado exitosamente. El stock ha sido devuelto automáticamente.",
                "orderNumber", cancelledOrder.getOrderNumber()
            ));

        } catch (Exception e) {
            log.error("Error cancelling order", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Error al cancelar el pedido: " + e.getMessage()
            ));
        }
    }

    /**
     * Get active promotions (AJAX endpoint)
     */
    @GetMapping("/promotions/active")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getActivePromotions() {
        log.debug("Fetching active promotions for customer");
        
        try {
            List<Promotion> promotions = promotionService.findActivePromotions();
            
            List<Map<String, Object>> promotionData = promotions.stream()
                    .map(this::convertPromotionToMap)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(promotionData);
            
        } catch (Exception e) {
            log.error("Error fetching active promotions", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Update customer profile (WITHOUT password) - Using DTO
     */
    @PostMapping("/profile/update")
    public String updateProfile(
            @Valid @ModelAttribute UpdateProfileDTO profileDTO,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Customer {} updating profile", authentication.getName());
        
        try {
            Customer existing = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Validate form errors
            if (bindingResult.hasErrors()) {
                log.warn("Validation errors found during profile update");
                model.addAttribute("customer", existing);
                model.addAttribute("profileDTO", profileDTO);
                model.addAttribute("passwordDTO", new ChangePasswordDTO());
                return "client/profile";
            }
            
            // Validate unique constraints (except for current customer)
            if (!existing.getUsername().equalsIgnoreCase(profileDTO.getUsername()) && 
                customerService.existsByUsername(profileDTO.getUsername())) {
                bindingResult.rejectValue("username", "error.customer", "El nombre de usuario ya está en uso");
                model.addAttribute("customer", existing);
                model.addAttribute("profileDTO", profileDTO);
                model.addAttribute("passwordDTO", new ChangePasswordDTO());
                return "client/profile";
            }
            
            if (!existing.getPhone().equals(profileDTO.getPhone()) && 
                customerService.existsByPhone(profileDTO.getPhone())) {
                bindingResult.rejectValue("phone", "error.customer", "El teléfono ya está registrado");
                model.addAttribute("customer", existing);
                model.addAttribute("profileDTO", profileDTO);
                model.addAttribute("passwordDTO", new ChangePasswordDTO());
                return "client/profile";
            }
            
            // Update only allowed fields from DTO
            existing.setFullName(profileDTO.getFullName());
            existing.setUsername(profileDTO.getUsername());
            existing.setPhone(profileDTO.getPhone());
            existing.setAddress(profileDTO.getAddress());
            
            customerService.update(existing.getIdCustomer(), existing);
            
            log.info("Customer profile updated successfully: {}", existing.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Perfil actualizado exitosamente");
            return "redirect:/client/profile";
            
        } catch (Exception e) {
            log.error("Error updating customer profile", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al actualizar perfil: " + e.getMessage());
            return "redirect:/client/profile";
        }
    }

    /**
     * Change customer password (SEPARATE endpoint) - Using DTO
     */
    @PostMapping("/profile/change-password")
    public String changePassword(
            @Valid @ModelAttribute ChangePasswordDTO passwordDTO,
            BindingResult bindingResult,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        log.info("Customer {} changing password", authentication.getName());
        
        try {
            // Validate form errors
            if (bindingResult.hasErrors()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Por favor corrige los errores en el formulario");
                return "redirect:/client/profile";
            }
            
            // Validate passwords match
            if (!passwordDTO.passwordsMatch()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Las contraseñas no coinciden");
                return "redirect:/client/profile";
            }
            
            Customer existing = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Encode the new password before updating
            String encodedPassword = passwordEncoder.encode(passwordDTO.getNewPassword());
            existing.setPassword(encodedPassword);
            customerService.update(existing.getIdCustomer(), existing);
            
            log.info("Customer password changed successfully: {}", existing.getUsername());
            redirectAttributes.addFlashAttribute("successMessage", "Contraseña actualizada exitosamente");
            return "redirect:/client/profile";
            
        } catch (Exception e) {
            log.error("Error changing customer password", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cambiar contraseña: " + e.getMessage());
            return "redirect:/client/profile";
        }
    }

    /**
     * Show customer profile
     */
    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        log.debug("Customer {} accessing profile", authentication.getName());
        
        try {
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Create DTOs for form binding
            UpdateProfileDTO profileDTO = new UpdateProfileDTO();
            profileDTO.setFullName(customer.getFullName());
            profileDTO.setUsername(customer.getUsername());
            profileDTO.setPhone(customer.getPhone());
            profileDTO.setAddress(customer.getAddress());
            
            model.addAttribute("customer", customer); // For display (email, etc.)
            model.addAttribute("profileDTO", profileDTO); // For profile form binding
            model.addAttribute("passwordDTO", new ChangePasswordDTO()); // For password form binding
            return "client/profile";
            
        } catch (Exception e) {
            log.error("Error loading customer profile", e);
            model.addAttribute("errorMessage", "Error al cargar perfil: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Show customer review form
     */
    @GetMapping("/review")
    public String showReviewForm(Authentication authentication, Model model) {
        log.debug("Customer {} accessing review form", authentication.getName());
        
        try {
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Check if customer already has a review
            Optional<Review> existingReview = reviewService.getReviewByCustomer(customer);
            
            model.addAttribute("customer", customer);
            model.addAttribute("existingReview", existingReview.orElse(null));
            model.addAttribute("hasReview", existingReview.isPresent());
            
            return "client/review";
            
        } catch (Exception e) {
            log.error("Error loading review form", e);
            model.addAttribute("errorMessage", "Error al cargar formulario: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Submit or update customer review
     */
    @PostMapping("/review")
    public String submitReview(
            @RequestParam Integer rating,
            @RequestParam String comment,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        log.info("Customer {} submitting review", authentication.getName());
        
        try {
            Customer customer = customerService.findByUsernameOrEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
            
            // Validate input
            if (rating == null || rating < 1 || rating > 5) {
                redirectAttributes.addFlashAttribute("errorMessage", "La calificación debe estar entre 1 y 5 estrellas");
                return "redirect:/client/review";
            }
            
            if (comment == null || comment.trim().length() < 10) {
                redirectAttributes.addFlashAttribute("errorMessage", "El comentario debe tener al menos 10 caracteres");
                return "redirect:/client/review";
            }
            
            if (comment.trim().length() > 500) {
                redirectAttributes.addFlashAttribute("errorMessage", "El comentario no puede exceder 500 caracteres");
                return "redirect:/client/review";
            }
            
            // Create or update review
            reviewService.createOrUpdateReview(customer, rating, comment.trim());
            
            redirectAttributes.addFlashAttribute("successMessage", 
                    "¡Gracias por tu reseña! Está pendiente de aprobación y aparecerá en la página pronto.");
            
            return "redirect:/client/dashboard";
            
        } catch (Exception e) {
            log.error("Error submitting review", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al enviar reseña: " + e.getMessage());
            return "redirect:/client/review";
        }
    }

    // ========== Helper Methods ==========

    private Map<String, Object> convertPromotionToMap(Promotion promotion) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", promotion.getIdPromotion());
        map.put("name", promotion.getName());
        map.put("description", promotion.getDescription());
        map.put("type", promotion.getPromotionType().name());
        map.put("promotionType", promotion.getPromotionType().name()); // Added for JavaScript compatibility
        map.put("imageUrl", promotion.getImageUrl());
        
        // Add type-specific fields and display label
        if (promotion.getPromotionType() == PromotionType.BUY_X_PAY_Y) {
            map.put("buyQuantity", promotion.getBuyQuantity());
            map.put("payQuantity", promotion.getPayQuantity());
            map.put("displayLabel", promotion.getBuyQuantity() + "x" + promotion.getPayQuantity());
        } else if (promotion.getPromotionType() == PromotionType.PERCENTAGE_DISCOUNT) {
            map.put("discountPercentage", promotion.getDiscountPercentage());
            map.put("displayLabel", promotion.getDiscountPercentage().setScale(2) + "% OFF");
        } else if (promotion.getPromotionType() == PromotionType.FIXED_AMOUNT_DISCOUNT) {
            map.put("discountAmount", promotion.getDiscountAmount());
            map.put("displayLabel", "$" + promotion.getDiscountAmount() + " OFF");
        }
        
        // Add item IDs
        List<Long> itemIds = promotion.getItems().stream()
                .map(ItemMenu::getIdItemMenu)
                .collect(Collectors.toList());
        map.put("itemIds", itemIds);
        
        return map;
    }
}
