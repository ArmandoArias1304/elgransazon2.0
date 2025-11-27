package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CustomerService;
import com.aatechsolutions.elgransazon.application.service.EmailVerificationService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Customer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for Customer authentication and registration
 * Handles login, register, and logout for customers
 */
@Controller
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class ClientAuthController {

    private final CustomerService customerService;
    private final SystemConfigurationService systemConfigurationService;
    private final EmailVerificationService emailVerificationService;

    /**
     * Show customer login form
     */
    @GetMapping("/login")
    public String showLoginForm(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            Model model) {
        
        log.debug("Displaying customer login form");
        
        // Add system configuration for branding
        model.addAttribute("config", systemConfigurationService.getConfiguration());
        
        if (error != null) {
            if ("employeeAttempt".equals(error)) {
                log.warn("Employee attempted to login via client login page");
                model.addAttribute("errorType", "employeeAttempt");
                model.addAttribute("error", "Este acceso es solo para clientes");
            } else {
                model.addAttribute("error", "Usuario o contraseña incorrectos");
            }
        }
        
        if (logout != null) {
            model.addAttribute("message", "Has cerrado sesión exitosamente");
        }
        
        return "auth/loginClient";
    }

    /**
     * Show customer registration form
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        log.debug("Displaying customer registration form");
        model.addAttribute("customer", new Customer());
        return "auth/registerClient";
    }

    /**
     * Process customer registration
     */
    @PostMapping("/register")
    public String registerCustomer(
            @Valid @ModelAttribute("customer") Customer customer,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        log.info("Processing customer registration: {}", customer.getEmail());
        
        // Validate form
        if (bindingResult.hasErrors()) {
            log.warn("Validation errors in customer registration: {}", bindingResult.getAllErrors());
            return "auth/registerClient";
        }
        
        try {
            // Check if username already exists
            if (customerService.existsByUsername(customer.getUsername())) {
                bindingResult.rejectValue("username", "error.customer", "El nombre de usuario ya está en uso");
                return "auth/registerClient";
            }
            
            // Check if email already exists
            if (customerService.existsByEmail(customer.getEmail())) {
                bindingResult.rejectValue("email", "error.customer", "El correo electrónico ya está registrado");
                return "auth/registerClient";
            }
            
            // Check if phone already exists
            if (customerService.existsByPhone(customer.getPhone())) {
                bindingResult.rejectValue("phone", "error.customer", "El teléfono ya está registrado");
                return "auth/registerClient";
            }
            
            // Create customer
            Customer newCustomer = customerService.create(customer);
            
            log.info("Customer registered successfully: {}", customer.getEmail());
            
            // Send verification email
            try {
                emailVerificationService.createOrReuseToken(newCustomer);
                log.info("Verification email sent to: {}", newCustomer.getEmail());
                
                redirectAttributes.addFlashAttribute("successMessage", 
                    "¡Registro exitoso! Te hemos enviado un correo de verificación a " + newCustomer.getEmail() + 
                    ". Por favor verifica tu correo antes de iniciar sesión.");
            } catch (Exception e) {
                log.error("Error sending verification email", e);
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Registro exitoso. Sin embargo, hubo un problema al enviar el correo de verificación. " +
                    "Por favor contacta a soporte.");
            }
            
            return "redirect:/client/login";
            
        } catch (Exception e) {
            log.error("Error registering customer", e);
            model.addAttribute("errorMessage", "Error al registrar el cliente: " + e.getMessage());
            return "auth/registerClient";
        }
    }
}
