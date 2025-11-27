package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.*;
import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Waiter Payment processing
 * Handles payment of delivered orders by waiter (non-cash only)
 */
@Controller
@RequestMapping("/waiter/payments")
@PreAuthorize("hasRole('ROLE_WAITER')")
@Slf4j
public class WaiterPaymentController {

    private final WaiterOrderServiceImpl waiterOrderService;
    private final SystemConfigurationService systemConfigurationService;
    private final OrderRepository orderRepository;
    private final EmployeeService employeeService;

    public WaiterPaymentController(
            @Qualifier("waiterOrderService") WaiterOrderServiceImpl waiterOrderService,
            SystemConfigurationService systemConfigurationService,
            OrderRepository orderRepository,
            EmployeeService employeeService) {
        this.waiterOrderService = waiterOrderService;
        this.systemConfigurationService = systemConfigurationService;
        this.orderRepository = orderRepository;
        this.employeeService = employeeService;
    }

    /**
     * Show payment form for an order
     * Only DELIVERED orders can be paid
     * Waiters can only collect NON-CASH payments
     */
    @GetMapping("/form/{orderId}")
    public String showPaymentForm(
            @PathVariable Long orderId,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.debug("Waiter {} displaying payment form for order ID: {}", username, orderId);

        return waiterOrderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // Validate that order is in DELIVERED status
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
                        return "redirect:/waiter/orders";
                    }

                    // Validate that payment method is NOT cash
                    if (order.getPaymentMethod() == PaymentMethodType.CASH) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Los meseros no pueden cobrar pagos en EFECTIVO. Por favor, dirija al cliente a caja.");
                        return "redirect:/waiter/orders";
                    }

                    // Get system configuration
                    SystemConfiguration config = systemConfigurationService.getConfiguration();
                    
                    // Get enabled payment methods (excluding CASH)
                    Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
                    List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .filter(method -> method != PaymentMethodType.CASH) // Exclude CASH
                        .collect(Collectors.toList());

                    // Check if there are enabled non-cash payment methods
                    if (enabledPaymentMethods.isEmpty()) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "No hay métodos de pago no-efectivo habilitados en la configuración del sistema");
                        return "redirect:/waiter/orders";
                    }

                    model.addAttribute("order", order);
                    model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                    model.addAttribute("currentPaymentMethod", order.getPaymentMethod().name());
                    model.addAttribute("currentRole", "waiter");
                    
                    return "waiter/payments/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Orden no encontrada");
                    return "redirect:/waiter/orders";
                });
    }

    /**
     * Process payment for an order
     * Waiter can only collect NON-CASH payment methods
     */
    @PostMapping("/process/{orderId}")
    public String processPayment(
            @PathVariable Long orderId,
            @RequestParam PaymentMethodType paymentMethod,
            @RequestParam(required = false, defaultValue = "0") BigDecimal tip,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        
        String username = authentication.getName();
        log.info("Waiter {} processing payment for order ID: {}", username, orderId);
        log.info("Payment method: {}, Tip: {}", paymentMethod, tip);

        try {
            // Validate that payment method is NOT cash
            if (paymentMethod == PaymentMethodType.CASH) {
                throw new IllegalStateException("Los meseros no pueden cobrar pagos en EFECTIVO. Por favor, dirija al cliente a caja.");
            }

            // Find the order
            Order order = waiterOrderService.findByIdWithDetails(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada"));

            // Validate that order is in DELIVERED status
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new IllegalStateException("Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
            }

            // Get system configuration to validate payment method
            SystemConfiguration config = systemConfigurationService.getConfiguration();
            if (!config.isPaymentMethodEnabled(paymentMethod)) {
                throw new IllegalStateException("El método de pago seleccionado no está habilitado: " + paymentMethod.getDisplayName());
            }

            // Validate tip is not negative
            if (tip == null) {
                tip = BigDecimal.ZERO;
            }
            if (tip.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("La propina no puede ser negativa");
            }

            // Get current waiter employee
            Employee waiter = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Mesero no encontrado"));

            // Set tip, payment method, and paidBy
            order.setTip(tip);
            order.setPaymentMethod(paymentMethod);
            order.setPaidBy(waiter);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now());
            
            // Save order first with tip, payment method, and paidBy
            orderRepository.save(order);
            log.info("Order {} updated with tip: {}, payment method: {}, and paid by: {}", 
                     order.getOrderNumber(), tip, paymentMethod, waiter.getFullName());

            // Change status to PAID
            // This will automatically free the table if applicable
            waiterOrderService.changeStatus(orderId, OrderStatus.PAID, username);

            log.info("Payment processed successfully by waiter {} for order: {}", username, order.getOrderNumber());
            
            // Reload order to get updated values
            order = waiterOrderService.findByIdWithDetails(orderId).orElse(order);
            
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pago procesado exitosamente para el pedido " + order.getOrderNumber() + 
                    ". Total pagado: " + order.getFormattedTotalWithTip());
            
            return "redirect:/waiter/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error processing payment: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/waiter/payments/form/" + orderId;

        } catch (Exception e) {
            log.error("Error processing payment for order ID: " + orderId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar el pago: " + e.getMessage());
            return "redirect:/waiter/payments/form/" + orderId;
        }
    }
}
