package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Promotion entity representing promotional offers for menu items
 * Supports three types: Buy X Pay Y, Percentage Discount, and Fixed Amount Discount
 */
@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idPromotion"})
@ToString(exclude = {"items"})
public class Promotion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_promotion")
    private Long idPromotion;

    @NotBlank(message = "El nombre de la promoción es requerido")
    @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Size(max = 500, message = "La URL de la imagen no puede exceder 500 caracteres")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // ========== Promotion Type ==========

    @NotNull(message = "El tipo de promoción es requerido")
    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_type", nullable = false, length = 30)
    private PromotionType promotionType;

    // ========== Discount Configuration ==========

    /**
     * For BUY_X_PAY_Y: The quantity to buy (X)
     * Example: Buy 2 (buyQuantity=2), Pay 1 (payQuantity=1)
     */
    @Min(value = 1, message = "La cantidad a comprar debe ser al menos 1")
    @Column(name = "buy_quantity")
    private Integer buyQuantity;

    /**
     * For BUY_X_PAY_Y: The quantity to pay for (Y)
     * Example: Buy 2 (buyQuantity=2), Pay 1 (payQuantity=1)
     */
    @Min(value = 1, message = "La cantidad a pagar debe ser al menos 1")
    @Column(name = "pay_quantity")
    private Integer payQuantity;

    /**
     * For PERCENTAGE_DISCOUNT: The discount percentage (0-100)
     * Example: 20.00 for 20% off
     */
    @DecimalMin(value = "0.0", message = "El porcentaje debe ser al menos 0")
    @DecimalMax(value = "100.0", message = "El porcentaje no puede exceder 100")
    @Digits(integer = 3, fraction = 2, message = "El porcentaje debe tener máximo 3 dígitos y 2 decimales")
    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    /**
     * For FIXED_AMOUNT_DISCOUNT: The fixed discount amount
     * Example: 5.00 for $5 off
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "El monto debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "El monto debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    // ========== Validity Period ==========

    @NotNull(message = "La fecha de inicio es requerida")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull(message = "La fecha de fin es requerida")
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Days of week when promotion is valid
     * Stored as comma-separated values: MONDAY,FRIDAY,SATURDAY
     */
    @NotBlank(message = "Debe seleccionar al menos un día de la semana")
    @Column(name = "valid_days", nullable = false, length = 100)
    private String validDays;

    // ========== Status and Priority ==========

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Priority for applying promotions when multiple are available
     * Higher number = higher priority
     */
    @NotNull(message = "La prioridad es requerida")
    @Min(value = 1, message = "La prioridad debe ser al menos 1")
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    // ========== Relationships ==========

    /**
     * Many-to-Many relationship with ItemMenu
     * A promotion can apply to many items, and an item can have many promotions
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "promotion_items",
        joinColumns = @JoinColumn(name = "id_promotion"),
        inverseJoinColumns = @JoinColumn(name = "id_item_menu")
    )
    @Builder.Default
    private List<ItemMenu> items = new ArrayList<>();

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.active == null) {
            this.active = true;
        }
        if (this.priority == null) {
            this.priority = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ========== Business Methods ==========

    /**
     * Add an item to this promotion
     */
    public void addItem(ItemMenu item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
    }

    /**
     * Remove an item from this promotion
     */
    public void removeItem(ItemMenu item) {
        if (this.items != null) {
            this.items.remove(item);
        }
    }

    /**
     * Clear all items from this promotion
     */
    public void clearItems() {
        if (this.items != null) {
            this.items.clear();
        }
    }

    /**
     * Check if promotion is currently valid (date and active status)
     */
    public boolean isValidNow() {
        LocalDate today = LocalDate.now();
        return Boolean.TRUE.equals(active) 
            && !today.isBefore(startDate) 
            && !today.isAfter(endDate)
            && isValidForDay(today.getDayOfWeek());
    }

    /**
     * Check if promotion is valid for a specific day of week
     */
    public boolean isValidForDay(DayOfWeek dayOfWeek) {
        if (validDays == null || validDays.isEmpty()) {
            return false;
        }
        return validDays.contains(dayOfWeek.name());
    }

    /**
     * Get list of valid days as DayOfWeek enum
     */
    public Set<DayOfWeek> getValidDaysSet() {
        Set<DayOfWeek> days = new HashSet<>();
        if (validDays != null && !validDays.isEmpty()) {
            String[] dayArray = validDays.split(",");
            for (String day : dayArray) {
                try {
                    days.add(DayOfWeek.valueOf(day.trim()));
                } catch (IllegalArgumentException e) {
                    // Skip invalid day
                }
            }
        }
        return days;
    }

    /**
     * Set valid days from a set of DayOfWeek
     */
    public void setValidDaysFromSet(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            this.validDays = "";
            return;
        }
        this.validDays = String.join(",", 
            days.stream()
                .map(DayOfWeek::name)
                .sorted()
                .toArray(String[]::new)
        );
    }

    /**
     * Calculate discounted price for an item
     * @param originalPrice The original price of the item
     * @param quantity The quantity being purchased
     * @return The discounted price (total for all items)
     */
    public BigDecimal calculateDiscountedPrice(BigDecimal originalPrice, int quantity) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        switch (promotionType) {
            case BUY_X_PAY_Y:
                return calculateBuyXPayY(originalPrice, quantity);
            
            case PERCENTAGE_DISCOUNT:
                return calculatePercentageDiscount(originalPrice, quantity);
            
            case FIXED_AMOUNT_DISCOUNT:
                return calculateFixedDiscount(originalPrice, quantity);
            
            default:
                return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /**
     * Calculate price for Buy X Pay Y promotion
     */
    private BigDecimal calculateBuyXPayY(BigDecimal originalPrice, int quantity) {
        if (buyQuantity == null || payQuantity == null || buyQuantity <= 0 || payQuantity <= 0) {
            return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }

        // Calculate how many complete "sets" of the promotion
        int promotionSets = quantity / buyQuantity;
        int remainingItems = quantity % buyQuantity;

        // Price = (sets * payQuantity * price) + (remaining * price)
        BigDecimal promotionPrice = originalPrice
            .multiply(BigDecimal.valueOf(promotionSets * payQuantity));
        BigDecimal remainingPrice = originalPrice
            .multiply(BigDecimal.valueOf(remainingItems));

        return promotionPrice.add(remainingPrice);
    }

    /**
     * Calculate price for percentage discount
     */
    private BigDecimal calculatePercentageDiscount(BigDecimal originalPrice, int quantity) {
        if (discountPercentage == null || discountPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }

        // Price = originalPrice * (1 - percentage/100) * quantity
        BigDecimal multiplier = BigDecimal.ONE
            .subtract(discountPercentage.divide(BigDecimal.valueOf(100)));
        
        return originalPrice
            .multiply(multiplier)
            .multiply(BigDecimal.valueOf(quantity))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate price for fixed amount discount
     */
    private BigDecimal calculateFixedDiscount(BigDecimal originalPrice, int quantity) {
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalPrice.multiply(BigDecimal.valueOf(quantity));
        }

        // Discounted price per item (cannot be negative)
        BigDecimal discountedPrice = originalPrice.subtract(discountAmount);
        if (discountedPrice.compareTo(BigDecimal.ZERO) < 0) {
            discountedPrice = BigDecimal.ZERO;
        }

        return discountedPrice
            .multiply(BigDecimal.valueOf(quantity))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Get display label for the promotion (for UI)
     * Examples: "2x1", "20% OFF", "-$5"
     */
    public String getDisplayLabel() {
        switch (promotionType) {
            case BUY_X_PAY_Y:
                return buyQuantity + "x" + payQuantity;
            
            case PERCENTAGE_DISCOUNT:
                return discountPercentage.stripTrailingZeros().toPlainString() + "% OFF";
            
            case FIXED_AMOUNT_DISCOUNT:
                return "-$" + discountAmount.stripTrailingZeros().toPlainString();
            
            default:
                return "Promoción";
        }
    }

    /**
     * Validate promotion configuration based on type
     */
    public boolean isValidConfiguration() {
        switch (promotionType) {
            case BUY_X_PAY_Y:
                return buyQuantity != null && payQuantity != null 
                    && buyQuantity > 0 && payQuantity > 0 
                    && buyQuantity > payQuantity;
            
            case PERCENTAGE_DISCOUNT:
                return discountPercentage != null 
                    && discountPercentage.compareTo(BigDecimal.ZERO) > 0
                    && discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0;
            
            case FIXED_AMOUNT_DISCOUNT:
                return discountAmount != null 
                    && discountAmount.compareTo(BigDecimal.ZERO) > 0;
            
            default:
                return false;
        }
    }
}
