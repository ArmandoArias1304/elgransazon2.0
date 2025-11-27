package com.aatechsolutions.elgransazon.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.aatechsolutions.elgransazon.application.service.EmailVerificationService;

/**
 * Controller for email verification
 */
@Controller
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     * Endpoint para verificar email (GET porque viene desde un link en el email)
     */
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, Model model) {
        log.info("Email verification request received");
        
        try {
            emailVerificationService.verifyEmail(token);
            log.info("Email verified successfully");
            
            model.addAttribute("success", true);
            model.addAttribute("message", "¡Tu correo ha sido verificado exitosamente!");
            model.addAttribute("redirectUrl", "/client/login");
            model.addAttribute("redirectText", "Ir al inicio de sesión");
            
        } catch (IllegalArgumentException e) {
            log.error("Email verification failed: {}", e.getMessage());
            
            model.addAttribute("success", false);
            model.addAttribute("message", e.getMessage());
            model.addAttribute("redirectUrl", "/client/login");
            model.addAttribute("redirectText", "Volver al inicio de sesión");
        }
        
        return "client/verify-email-result";
    }
}
