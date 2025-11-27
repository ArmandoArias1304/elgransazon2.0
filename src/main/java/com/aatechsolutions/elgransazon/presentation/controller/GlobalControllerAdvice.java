package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Global Controller Advice para añadir atributos comunes a todas las vistas.
 * Esto permite que el nombre del restaurante esté disponible en todos los templates.
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private SystemConfigurationService systemConfigurationService;

    /**
     * Añade la configuración del sistema a todas las vistas automáticamente.
     * Esto permite acceder a ${systemConfig.restaurantName} en cualquier template.
     */
    @ModelAttribute("systemConfig")
    public SystemConfiguration addSystemConfiguration() {
        return systemConfigurationService.getConfiguration();
    }
}
