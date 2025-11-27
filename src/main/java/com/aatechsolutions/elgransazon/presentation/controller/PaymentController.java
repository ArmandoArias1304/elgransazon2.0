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
 * Controller for Payment processing
 * Handles payment of delivered orders
 * Accessible by ADMIN, MANAGER, and WAITER roles
 */
@Controller
@RequestMapping("/admin/payments")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER')")
@Slf4j
public class PaymentController {

    private final OrderService orderService;
    private final SystemConfigurationService systemConfigurationService;
    private final OrderRepository orderRepository;
    private final TicketPdfService ticketPdfService;

    // Constructor manual para inyectar adminOrderService específicamente
    public PaymentController(
            @Qualifier("adminOrderService") OrderService orderService,
            SystemConfigurationService systemConfigurationService,
            OrderRepository orderRepository,
            TicketPdfService ticketPdfService) {
        this.orderService = orderService;
        this.systemConfigurationService = systemConfigurationService;
        this.orderRepository = orderRepository;
        this.ticketPdfService = ticketPdfService;
    }

    /**
     * Show payment form for an order
     * Only DELIVERED orders can be paid
     */
    @GetMapping("/form/{orderId}")
    public String showPaymentForm(
            @PathVariable Long orderId,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.debug("Displaying payment form for order ID: {}", orderId);

        return orderService.findByIdWithDetails(orderId)
                .map(order -> {
                    // Validate that order is in DELIVERED status
                    if (order.getStatus() != OrderStatus.DELIVERED) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "Solo se pueden pagar órdenes con estado ENTREGADO. Estado actual: " + order.getStatus().getDisplayName());
                        return "redirect:/admin/orders";
                    }

                    // Get system configuration
                    SystemConfiguration config = systemConfigurationService.getConfiguration();
                    
                    // Get enabled payment methods
                    Map<PaymentMethodType, Boolean> paymentMethods = config.getPaymentMethods();
                    List<PaymentMethodType> enabledPaymentMethods = paymentMethods.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                    // Check if there are enabled payment methods
                    if (enabledPaymentMethods.isEmpty()) {
                        redirectAttributes.addFlashAttribute("errorMessage", 
                            "No hay métodos de pago habilitados en la configuración del sistema");
                        return "redirect:/admin/orders";
                    }

                    model.addAttribute("order", order);
                    model.addAttribute("enabledPaymentMethods", enabledPaymentMethods);
                    
                    return "admin/payments/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Orden no encontrada");
                    return "redirect:/admin/orders";
                });
    }

    /**
     * Process payment for an order
     */
    @PostMapping("/process/{orderId}")
    public String processPayment(
            @PathVariable Long orderId,
            @RequestParam PaymentMethodType paymentMethod,
            @RequestParam(required = false, defaultValue = "0") BigDecimal tip,
            Authentication authentication,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpSession session) {
        
        String username = authentication.getName();
        log.info("Processing payment for order ID: {} by user: {}", orderId, username);
        log.info("Payment method: {}, Tip: {}", paymentMethod, tip);

        try {
            // Find the order
            Order order = orderService.findByIdWithDetails(orderId)
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

            // Set tip and payment method
            order.setTip(tip);
            order.setPaymentMethod(paymentMethod);
            order.setUpdatedBy(username);
            order.setUpdatedAt(java.time.LocalDateTime.now()); // Explicitly set updatedAt
            
            // Save order first with tip and payment method
            orderRepository.save(order);
            log.info("Order {} updated with tip: {} and payment method: {}", 
                     order.getOrderNumber(), tip, paymentMethod);

            // Change status to PAID
            // NOTE: The OrderService.changeStatus() method will automatically free the table
            // when status changes to PAID, so we don't need to do it manually here
            orderService.changeStatus(orderId, OrderStatus.PAID, username);

            log.info("Payment processed successfully for order: {}", order.getOrderNumber());
            
            // Reload order to get updated values
            order = orderService.findByIdWithDetails(orderId).orElse(order);
            
            // Generate PDF ticket
            try {
                byte[] pdfBytes = ticketPdfService.generateTicket(order);
                session.setAttribute("ticketPdf", pdfBytes);
                session.setAttribute("ticketFilename", "ticket_" + order.getOrderNumber() + ".pdf");
                log.info("PDF ticket generated and stored in session");
            } catch (Exception e) {
                log.error("Error generating PDF ticket: {}", e.getMessage(), e);
                // Continue even if PDF generation fails
            }
            
            redirectAttributes.addFlashAttribute("successMessage",
                    "Pago procesado exitosamente para el pedido " + order.getOrderNumber() + 
                    ". Total pagado: " + order.getFormattedTotalWithTip());
            redirectAttributes.addFlashAttribute("downloadTicket", true);
            
            return "redirect:/admin/orders";

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Validation error processing payment: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/payments/form/" + orderId;

        } catch (Exception e) {
            log.error("Error processing payment for order ID: " + orderId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error al procesar el pago: " + e.getMessage());
            return "redirect:/admin/payments/form/" + orderId;
        }
    }

    /**
     * Download PDF ticket from session
     */
    @GetMapping("/download-ticket")
    public org.springframework.http.ResponseEntity<byte[]> downloadTicket(
            jakarta.servlet.http.HttpSession session) {
        
        byte[] pdfBytes = (byte[]) session.getAttribute("ticketPdf");
        String filename = (String) session.getAttribute("ticketFilename");
        
        if (pdfBytes == null || filename == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        
        // Clear session attributes
        session.removeAttribute("ticketPdf");
        session.removeAttribute("ticketFilename");
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        
        return new org.springframework.http.ResponseEntity<>(pdfBytes, headers, org.springframework.http.HttpStatus.OK);
    }
}
