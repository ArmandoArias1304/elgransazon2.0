package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SystemConfiguration entity representing global system settings
 * Singleton pattern - only one configuration should exist
 */
@Entity
@Table(name = "system_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"businessHours", "socialNetworks"})
public class SystemConfiguration implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotBlank(message = "Restaurant name is required")
    @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
    @Column(name = "restaurant_name", nullable = false, length = 100)
    private String restaurantName;

    @Size(max = 255, message = "Slogan cannot exceed 255 characters")
    @Column(name = "slogan", length = 255)
    private String slogan;

    // Logo URL is now hardcoded to use static image
    @Transient
    private final String logoUrl = "/images/LogoVariante.png";

    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address cannot exceed 500 characters")
    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[+]?[0-9\\-\\s()]{7,20}$", message = "Phone format is invalid")
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @NotNull(message = "Tax rate is required")
    @DecimalMin(value = "0.0", message = "Tax rate must be at least 0")
    @DecimalMax(value = "100.0", message = "Tax rate cannot exceed 100")
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @NotNull(message = "Average consumption time is required")
    @Min(value = 30, message = "Average consumption time must be at least 30 minutes")
    @Max(value = 480, message = "Average consumption time cannot exceed 480 minutes (8 hours)")
    @Column(name = "average_consumption_time_minutes", nullable = false)
    @Builder.Default
    private Integer averageConsumptionTimeMinutes = 120; // Default: 2 hours

    // Payment methods with enable/disable status
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "system_payment_methods", joinColumns = @JoinColumn(name = "system_configuration_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "enabled")
    @Builder.Default
    private Map<PaymentMethodType, Boolean> paymentMethods = new HashMap<>();

    @OneToMany(mappedBy = "systemConfiguration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dayOfWeek ASC")
    @Builder.Default
    private List<BusinessHours> businessHours = new ArrayList<>();

    @OneToMany(mappedBy = "systemConfiguration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<SocialNetwork> socialNetworks = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        // Initialize payment methods if not set
        if (this.paymentMethods == null || this.paymentMethods.isEmpty()) {
            this.paymentMethods = new HashMap<>();
            this.paymentMethods.put(PaymentMethodType.CASH, true);
            this.paymentMethods.put(PaymentMethodType.CREDIT_CARD, true);
            this.paymentMethods.put(PaymentMethodType.DEBIT_CARD, true);
            this.paymentMethods.put(PaymentMethodType.TRANSFER, false); // Disabled by default
        }
    }

    // Helper methods for managing business hours
    public void addBusinessHours(BusinessHours hours) {
        businessHours.add(hours);
        hours.setSystemConfiguration(this);
    }

    public void removeBusinessHours(BusinessHours hours) {
        businessHours.remove(hours);
        hours.setSystemConfiguration(null);
    }

    public void clearBusinessHours() {
        businessHours.forEach(hours -> hours.setSystemConfiguration(null));
        businessHours.clear();
    }

    // Helper methods for managing social networks
    public void addSocialNetwork(SocialNetwork network) {
        socialNetworks.add(network);
        network.setSystemConfiguration(this);
    }

    public void removeSocialNetwork(SocialNetwork network) {
        socialNetworks.remove(network);
        network.setSystemConfiguration(null);
    }

    public void clearSocialNetworks() {
        socialNetworks.forEach(network -> network.setSystemConfiguration(null));
        socialNetworks.clear();
    }

    // Helper method to check if a day is a work day
    // A day is a work day if it has business hours and is NOT closed
    public boolean isWorkDay(DayOfWeek day) {
        return businessHours.stream()
                .anyMatch(hours -> hours.getDayOfWeek().equals(day) && !hours.getIsClosed());
    }

    // Helper method to check if a payment method is enabled
    public boolean isPaymentMethodEnabled(PaymentMethodType type) {
        return paymentMethods.getOrDefault(type, false);
    }

    // Helper method to get active social networks
    public List<SocialNetwork> getActiveSocialNetworks() {
        return socialNetworks.stream()
                .filter(SocialNetwork::getActive)
                .toList();
    }

    // Helper method to get work days sorted
    // Returns all days that are NOT closed, sorted by ordinal
    public List<DayOfWeek> getSortedWorkDays() {
        return businessHours.stream()
                .filter(hours -> !hours.getIsClosed())
                .map(BusinessHours::getDayOfWeek)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }

    // Get business hours for a specific day
    public Optional<BusinessHours> getBusinessHoursForDay(DayOfWeek day) {
        return businessHours.stream()
                .filter(hours -> hours.getDayOfWeek().equals(day))
                .findFirst();
    }

    /**
     * Get formatted average consumption time (e.g., "2 horas" or "90 minutos")
     */
    public String getAverageConsumptionTimeDisplay() {
        if (averageConsumptionTimeMinutes == null) {
            return "N/A";
        }
        
        int hours = averageConsumptionTimeMinutes / 60;
        int minutes = averageConsumptionTimeMinutes % 60;
        
        if (hours > 0 && minutes == 0) {
            return hours == 1 ? "1 hora" : hours + " horas";
        } else if (hours > 0) {
            return hours + "h " + minutes + "min";
        } else {
            return minutes + " minutos";
        }
    }
}
