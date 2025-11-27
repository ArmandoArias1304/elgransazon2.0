package com.aatechsolutions.elgransazon.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aatechsolutions.elgransazon.application.service.PasswordResetService;

/**
 * Controller for password reset
 */
@Controller
@RequestMapping("/client")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Mostrar formulario para solicitar restablecimiento de contraseña
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "client/forgot-password";
    }

    /**
     * Procesar solicitud de restablecimiento
     */
    @PostMapping("/password-reset/request")
    public String requestPasswordReset(@RequestParam String email, RedirectAttributes redirectAttributes) {
        log.info("Password reset request for: {}", email);
        
        try {
            passwordResetService.requestPasswordReset(email);
            redirectAttributes.addFlashAttribute("success", true);
            redirectAttributes.addFlashAttribute("message", 
                "Si el correo existe en nuestro sistema, se ha enviado un enlace de restablecimiento.");
            
        } catch (Exception e) {
            log.error("Error processing password reset request", e);
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", "Error al procesar la solicitud. Intenta nuevamente.");
        }
        
        return "redirect:/client/forgot-password";
    }

    /**
     * Mostrar formulario para ingresar nueva contraseña
     */
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "client/reset-password";
    }

    /**
     * Procesar confirmación de nueva contraseña
     */
    @PostMapping("/password-reset/confirm")
    public String confirmPasswordReset(
            @RequestParam String token, 
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes) {
        
        log.info("Password reset confirmation attempt");
        
        try {
            // Validar que las contraseñas coincidan
            if (!newPassword.equals(confirmPassword)) {
                throw new IllegalArgumentException("Las contraseñas no coinciden.");
            }
            
            passwordResetService.confirmPasswordReset(token, newPassword);
            
            redirectAttributes.addFlashAttribute("success", true);
            redirectAttributes.addFlashAttribute("message", 
                "¡Contraseña restablecida con éxito! Ahora puedes iniciar sesión.");
            
            return "redirect:/client/login";
            
        } catch (IllegalArgumentException e) {
            log.error("Password reset failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", e.getMessage());
            redirectAttributes.addAttribute("token", token);
            return "redirect:/client/reset-password";
        }
    }
}
