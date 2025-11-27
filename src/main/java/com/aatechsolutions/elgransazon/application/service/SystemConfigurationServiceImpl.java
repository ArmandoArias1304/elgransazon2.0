package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.BusinessHours;
import com.aatechsolutions.elgransazon.domain.entity.DayOfWeek;
import com.aatechsolutions.elgransazon.domain.entity.PaymentMethodType;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.SystemConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Implementation of SystemConfigurationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SystemConfigurationServiceImpl implements SystemConfigurationService {

    private final SystemConfigurationRepository configurationRepository;

    @Override
    public SystemConfiguration getConfiguration() {
        log.debug("Fetching system configuration");
        Optional<SystemConfiguration> config = configurationRepository.findFirstConfiguration();
        if (config.isPresent()) {
            return config.get();
        }
        // Create default configuration in a new transaction if none exists
        return createDefaultConfiguration();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SystemConfiguration> getConfigurationById(Long id) {
        log.debug("Fetching configuration by ID: {}", id);
        return configurationRepository.findById(id);
    }

    @Override
    public SystemConfiguration updateConfiguration(SystemConfiguration configuration) {
        log.info("Updating system configuration");
        log.debug("Input averageConsumptionTimeMinutes: {}", configuration.getAverageConsumptionTimeMinutes());
        
        SystemConfiguration existingConfig = configurationRepository.findFirstConfiguration()
                .orElseThrow(() -> new IllegalStateException("System configuration not found"));
        
        log.debug("Existing averageConsumptionTimeMinutes before update: {}", existingConfig.getAverageConsumptionTimeMinutes());
        
        // Update fields
        existingConfig.setRestaurantName(configuration.getRestaurantName());
        existingConfig.setSlogan(configuration.getSlogan());
        existingConfig.setAddress(configuration.getAddress());
        existingConfig.setPhone(configuration.getPhone());
        existingConfig.setEmail(configuration.getEmail());
        existingConfig.setTaxRate(configuration.getTaxRate());
        existingConfig.setAverageConsumptionTimeMinutes(configuration.getAverageConsumptionTimeMinutes());
        
        log.debug("Existing averageConsumptionTimeMinutes after update: {}", existingConfig.getAverageConsumptionTimeMinutes());
        
        if (configuration.getPaymentMethods() != null) {
            existingConfig.setPaymentMethods(configuration.getPaymentMethods());
        }
        
        SystemConfiguration saved = configurationRepository.save(existingConfig);
        log.info("System configuration updated successfully");
        return saved;
    }

    @Override
    public SystemConfiguration createInitialConfiguration(SystemConfiguration configuration) {
        log.info("Creating initial system configuration");
        
        if (configurationExists()) {
            throw new IllegalStateException("System configuration already exists. Use update instead.");
        }
        
        // Initialize payment methods if not set
        if (configuration.getPaymentMethods() == null || configuration.getPaymentMethods().isEmpty()) {
            Map<PaymentMethodType, Boolean> paymentMethods = new HashMap<>();
            paymentMethods.put(PaymentMethodType.CASH, true);
            paymentMethods.put(PaymentMethodType.CREDIT_CARD, true);
            paymentMethods.put(PaymentMethodType.DEBIT_CARD, true);
            configuration.setPaymentMethods(paymentMethods);
        }
        
        SystemConfiguration saved = configurationRepository.save(configuration);
        log.info("Initial system configuration created successfully");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean configurationExists() {
        return configurationRepository.existsConfiguration();
    }

    @Override
    public SystemConfiguration updatePaymentMethods(Map<PaymentMethodType, Boolean> paymentMethods) {
        log.info("Updating payment methods");
        
        if (paymentMethods == null || paymentMethods.isEmpty()) {
            throw new IllegalArgumentException("Payment methods cannot be empty");
        }
        
        SystemConfiguration config = configurationRepository.findFirstConfiguration()
                .orElseThrow(() -> new IllegalStateException("System configuration not found"));
        config.setPaymentMethods(paymentMethods);
        
        SystemConfiguration saved = configurationRepository.save(config);
        log.info("Payment methods updated successfully");
        return saved;
    }

    @Override
    public SystemConfiguration updateTaxRate(BigDecimal taxRate) {
        log.info("Updating tax rate to: {}", taxRate);
        
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Tax rate must be between 0 and 100");
        }
        
        SystemConfiguration config = configurationRepository.findFirstConfiguration()
                .orElseThrow(() -> new IllegalStateException("System configuration not found"));
        config.setTaxRate(taxRate);
        
        SystemConfiguration saved = configurationRepository.save(config);
        log.info("Tax rate updated successfully");
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWorkDay(DayOfWeek day) {
        return configurationRepository.findFirstConfiguration()
                .map(config -> config.isWorkDay(day))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPaymentMethodEnabled(PaymentMethodType type) {
        return configurationRepository.findFirstConfiguration()
                .map(config -> config.isPaymentMethodEnabled(type))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public String getRestaurantName() {
        return configurationRepository.findFirstConfiguration()
                .map(SystemConfiguration::getRestaurantName)
                .orElse("Restaurante");
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTaxRate() {
        return configurationRepository.findFirstConfiguration()
                .map(SystemConfiguration::getTaxRate)
                .orElse(new BigDecimal("16.00"));
    }

    /**
     * Create a default configuration if none exists
     */
    private SystemConfiguration createDefaultConfiguration() {
        log.info("Creating default system configuration");
        
        // Define default work days (Monday to Saturday)
        Set<DayOfWeek> defaultWorkDays = new HashSet<>(Arrays.asList(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        ));
        
        Map<PaymentMethodType, Boolean> defaultPaymentMethods = new HashMap<>();
        defaultPaymentMethods.put(PaymentMethodType.CASH, true);
        defaultPaymentMethods.put(PaymentMethodType.CREDIT_CARD, true);
        defaultPaymentMethods.put(PaymentMethodType.DEBIT_CARD, true);
        
        SystemConfiguration defaultConfig = SystemConfiguration.builder()
                .restaurantName("Mi Restaurante")
                .slogan("El mejor sabor de la ciudad")
                .address("Dirección no configurada")
                .phone("0000-0000")
                .email("contacto@restaurant.com")
                .taxRate(new BigDecimal("16.00"))
                .averageConsumptionTimeMinutes(120) // 2 hours default
                .paymentMethods(defaultPaymentMethods)
                .build();
        
        // Save configuration first to get the ID
        SystemConfiguration saved = configurationRepository.save(defaultConfig);
        log.info("Default system configuration created with ID: {}", saved.getId());
        
        // Create default business hours for all days
        log.info("Creating default business hours for all days");
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean isWorkDay = defaultWorkDays.contains(day);
            BusinessHours hours = BusinessHours.builder()
                    .dayOfWeek(day)
                    .openTime(java.time.LocalTime.of(8, 0))   // 8:00 AM
                    .closeTime(java.time.LocalTime.of(22, 0))  // 10:00 PM
                    .isClosed(!isWorkDay) // Closed if not a work day (Sunday closed)
                    .systemConfiguration(saved)
                    .build();
            saved.addBusinessHours(hours);
            log.debug("Created business hours for {}: {} - {} (closed: {})", 
                    day.getDisplayName(), hours.getOpenTime(), hours.getCloseTime(), hours.getIsClosed());
        }
        
        // Save again with business hours
        saved = configurationRepository.save(saved);
        log.info("Default business hours created successfully for all 7 days");
        
        return saved;
    }
}
