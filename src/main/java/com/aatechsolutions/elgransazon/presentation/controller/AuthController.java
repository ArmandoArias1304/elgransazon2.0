package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for authentication-related views
 * Handles login and logout pages
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final SystemConfigurationService systemConfigurationService;

    /**
     * Display login page
     * 
     * @param error indicates if there was a login error
     * @param logout indicates if user just logged out
     * @param model Spring MVC model
     * @return login view name
     */
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model) {
        
        if (error != null) {
            if ("clientAttempt".equals(error)) {
                log.warn("Client attempted to login via employee login page");
                model.addAttribute("errorType", "clientAttempt");
                model.addAttribute("error", "Este acceso es solo para empleados");
            } else {
                log.warn("Login attempt failed");
                model.addAttribute("error", "Nombre de usuario o contraseña incorrectos");
            }
        }
        
        if (logout != null) {
            log.info("User logged out");
            model.addAttribute("message", "Has cerrado sesión correctamente");
        }
        
        // Get system configuration for logo and restaurant name
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        model.addAttribute("config", config);
        
        return "auth/login";
    }

    /**
     * Display help page (FAQ)
     * 
     * @return help view name
     */
    @GetMapping("/help")
    public String help() {
        log.info("User accessed help page");
        return "auth/help";
    }

    /**
     * Display support page (Contact)
     * 
     * @return support view name
     */
    @GetMapping("/support")
    public String support() {
        log.info("User accessed support page");
        return "auth/support";
    }

    /**
     * Display help page for clients (FAQ)
     * 
     * @return help client view name
     */
    @GetMapping("/helpClient")
    public String helpClient() {
        log.info("Client accessed help page");
        return "auth/helpClient";
    }

    /**
     * Display support page for clients (Contact)
     * 
     * @return support client view name
     */
    @GetMapping("/supportClient")
    public String supportClient() {
        log.info("Client accessed support page");
        return "auth/supportClient";
    }
}
