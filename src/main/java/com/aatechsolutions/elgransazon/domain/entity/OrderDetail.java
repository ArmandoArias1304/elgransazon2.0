package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OrderDetail entity representing individual items in an order
 */
@Entity
@Table(name = "order_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idOrderDetail"})
@ToString(exclude = {"order", "itemMenu"})
public class OrderDetail implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order_detail")
    private Long idOrderDetail;

    // ========== Relationships ==========

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_menu", nullable = false)
    private ItemMenu itemMenu;

    // ========== Quantity and Pricing ==========

    @NotNull(message = "La cantidad es requerida")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull(message = "El precio unitario es requerido")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @NotNull(message = "El subtotal es requerido")
    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    // ========== Promotion Fields ==========

    /**
     * Price of the item with promotion applied (per unit)
     * If no promotion: null or equals unitPrice
     * If promotion applied: discounted price per unit
     */
    @Digits(integer = 8, fraction = 2, message = "El precio promocional debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "promotion_applied_price", precision = 10, scale = 2)
    private BigDecimal promotionAppliedPrice;

    /**
     * ID of the promotion that was applied to this item
     * Null if no promotion was applied
     */
    @Column(name = "applied_promotion_id")
    private Long appliedPromotionId;

    // ========== Special Instructions ==========

    @Size(max = 500, message = "Los comentarios no pueden exceder 500 caracteres")
    @Column(name = "comments", length = 500)
    private String comments;

    // ========== Item Status (individual per item) ==========

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", length = 20, nullable = false)
    @Builder.Default
    private OrderStatus itemStatus = OrderStatus.PENDING;

    @Column(name = "is_new_item", nullable = false)
    @Builder.Default
    private Boolean isNewItem = false;

    @Column(name = "added_at")
    private LocalDateTime addedAt;

    @Column(name = "prepared_by")
    private String preparedBy;

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.itemStatus == null) {
            this.itemStatus = OrderStatus.PENDING;
        }
        if (this.isNewItem == null) {
            this.isNewItem = false;
        }
        if (this.addedAt == null) {
            this.addedAt = LocalDateTime.now();
        }
    }

    // ========== Business Methods ==========

    /**
     * Calculate subtotal from quantity and unit price
     * If promotion is applied, uses promotionAppliedPrice instead of unitPrice
     */
    public void calculateSubtotal() {
        if (this.quantity != null) {
            // Use promotional price if available, otherwise use regular price
            BigDecimal priceToUse = (this.promotionAppliedPrice != null) 
                ? this.promotionAppliedPrice 
                : this.unitPrice;
            
            if (priceToUse != null) {
                this.subtotal = priceToUse.multiply(BigDecimal.valueOf(this.quantity));
            }
        }
    }

    /**
     * Calculate savings from promotion
     * @return Amount saved, or ZERO if no promotion
     */
    public BigDecimal getSavings() {
        if (promotionAppliedPrice == null || unitPrice == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        
        // Savings per unit = unitPrice - promotionAppliedPrice
        BigDecimal savingsPerUnit = unitPrice.subtract(promotionAppliedPrice);
        
        // Total savings = savingsPerUnit * quantity
        return savingsPerUnit.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Check if this item has a promotion applied
     */
    public boolean hasPromotionApplied() {
        return promotionAppliedPrice != null && appliedPromotionId != null;
    }

    /**
     * Get formatted unit price
     */
    public String getFormattedUnitPrice() {
        if (unitPrice == null) {
            return "$0.00";
        }
        return String.format("$%.2f", unitPrice);
    }

    /**
     * Get formatted subtotal
     */
    public String getFormattedSubtotal() {
        if (subtotal == null) {
            return "$0.00";
        }
        return String.format("$%.2f", subtotal);
    }

    /**
     * Check if this item is newly added
     */
    public boolean isNew() {
        return Boolean.TRUE.equals(isNewItem);
    }

    /**
     * Mark this item as new (added after initial order)
     */
    public void markAsNew() {
        this.isNewItem = true;
        this.addedAt = LocalDateTime.now();
    }

    /**
     * Check if item is pending preparation
     */
    public boolean isPending() {
        return itemStatus == OrderStatus.PENDING;
    }

    /**
     * Check if item is in preparation
     */
    public boolean isInPreparation() {
        return itemStatus == OrderStatus.IN_PREPARATION;
    }

    /**
     * Check if item is ready
     */
    public boolean isReady() {
        return itemStatus == OrderStatus.READY;
    }

    /**
     * Check if item is delivered
     */
    public boolean isDelivered() {
        return itemStatus == OrderStatus.DELIVERED;
    }
}
