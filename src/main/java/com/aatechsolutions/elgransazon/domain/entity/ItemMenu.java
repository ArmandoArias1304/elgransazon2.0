package com.aatechsolutions.elgransazon.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ItemMenu entity representing menu items/dishes offered by the restaurant
 * Examples: "Hamburguesa Clásica", "Coca-Cola", "Ensalada César", etc.
 * Each item has a recipe (list of ingredients) and automatically manages inventory
 */
@Entity
@Table(name = "item_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"idItemMenu"})
@ToString(exclude = {"category", "ingredients"})
public class ItemMenu implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_menu")
    private Long idItemMenu;

    @NotBlank(message = "El nombre del platillo es requerido")
    @Size(min = 2, max = 200, message = "El nombre debe tener entre 2 y 200 caracteres")
    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    @Size(max = 1000, message = "La descripción no puede exceder 1000 caracteres")
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ========== Pricing ==========

    @NotNull(message = "El precio es requerido")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio debe ser mayor a 0")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener máximo 8 dígitos y 2 decimales")
    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    private BigDecimal price;

    // ========== Image ==========

    @Size(max = 500, message = "La URL de la imagen no puede exceder 500 caracteres")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // ========== Status ==========

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Indicates if the item is currently available for sale
     * Calculated automatically based on ingredient stock availability
     */
    @Column(name = "available", nullable = false)
    @Builder.Default
    private Boolean available = true;

    /**
     * Indicates if this item requires preparation by the chef
     * TRUE: Items like pizzas, burgers, hot dishes (chef must prepare)
     * FALSE: Items like sodas, bottled drinks, pre-packaged desserts (ready to serve)
     * Items with requiresPreparation=false will skip the chef and go directly to READY status
     */
    @Column(name = "requires_preparation", nullable = false)
    @Builder.Default
    private Boolean requiresPreparation = true;

    // ========== Relationships ==========

    /**
     * Many-to-One relationship with Category
     * Each menu item belongs to a specific category
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_category", nullable = false)
    private Category category;

    /**
     * One-to-Many relationship with ItemIngredient (Recipe)
     * List of ingredients required to prepare this menu item
     */
    @OneToMany(mappedBy = "itemMenu", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemIngredient> ingredients = new ArrayList<>();

    /**
     * Many-to-Many relationship with Promotion
     * An item can have multiple promotions, and a promotion can apply to multiple items
     */
    @ManyToMany(mappedBy = "items", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Promotion> promotions = new ArrayList<>();

    // ========== Timestamps ==========

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Lifecycle callback to set updatedAt before update operations
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Lifecycle callback to set createdAt before persist operations
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ========== Business Logic Methods ==========

    /**
     * Add an ingredient to this menu item's recipe
     */
    public void addIngredient(ItemIngredient itemIngredient) {
        this.ingredients.add(itemIngredient);
        itemIngredient.setItemMenu(this);
    }

    /**
     * Remove an ingredient from this menu item's recipe
     */
    public void removeIngredient(ItemIngredient itemIngredient) {
        this.ingredients.remove(itemIngredient);
        itemIngredient.setItemMenu(null);
    }

    /**
     * Clear all ingredients from the recipe
     */
    public void clearIngredients() {
        this.ingredients.clear();
    }

    /**
     * Check if the item has a recipe defined (at least one ingredient)
     */
    public boolean hasRecipe() {
        return ingredients != null && !ingredients.isEmpty();
    }

    /**
     * Get the total cost of all ingredients for this item
     * Used for calculating profit margins
     */
    public BigDecimal calculateIngredientsCost() {
        if (!hasRecipe()) {
            return BigDecimal.ZERO;
        }

        return ingredients.stream()
                .map(ItemIngredient::calculateCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate profit margin percentage
     * Formula: ((price - cost) / price) * 100
     */
    public BigDecimal calculateProfitMarginPercentage() {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal cost = calculateIngredientsCost();
        BigDecimal profit = price.subtract(cost);
        
        return profit.multiply(BigDecimal.valueOf(100))
                .divide(price, 2, RoundingMode.HALF_UP);
    }

    /**
     * Check if this item can be prepared based on current stock
     * @param quantity Number of items to prepare
     * @return true if there's enough stock for all ingredients
     */
    public boolean hasEnoughStock(int quantity) {
        if (!hasRecipe()) {
            return true; // Items without recipe are always available
        }

        return ingredients.stream()
                .allMatch(itemIngredient -> itemIngredient.hasEnoughStock(quantity));
    }

    /**
     * Update availability based on current stock
     * Should be called after stock changes
     */
    public void updateAvailability() {
        this.available = hasEnoughStock(1);
    }

    /**
     * Custom setter for requiresPreparation with logging
     * This helps debug form binding issues
     */
    public void setRequiresPreparation(Boolean requiresPreparation) {
        System.out.println("🔍🔍🔍 ItemMenu.setRequiresPreparation() called with value: " + requiresPreparation);
        System.out.println("🔍🔍🔍 Stack trace: " + Thread.currentThread().getStackTrace()[2]);
        this.requiresPreparation = requiresPreparation;
    }

    /**
     * Get formatted price with currency symbol
     */
    public String getFormattedPrice() {
        if (price == null) {
            return "$0.00";
        }
        return String.format("$%.2f", price);
    }

    /**
     * Get all active promotions for this item
     * @return List of promotions that are currently valid
     */
    public List<Promotion> getActivePromotions() {
        if (promotions == null || promotions.isEmpty()) {
            return new ArrayList<>();
        }
        
        return promotions.stream()
            .filter(Promotion::isValidNow)
            .sorted((p1, p2) -> p2.getPriority().compareTo(p1.getPriority())) // Higher priority first
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get the best promotion for this item (highest savings)
     * @return The best promotion, or null if none available
     */
    public Promotion getBestPromotion() {
        List<Promotion> activePromotions = getActivePromotions();
        if (activePromotions.isEmpty()) {
            return null;
        }

        // Find promotion with maximum discount
        return activePromotions.stream()
            .max((p1, p2) -> {
                BigDecimal savings1 = calculateSavings(p1, 1);
                BigDecimal savings2 = calculateSavings(p2, 1);
                return savings1.compareTo(savings2);
            })
            .orElse(activePromotions.get(0)); // Fallback to first (highest priority)
    }

    /**
     * Calculate promotional price for this item with a specific promotion
     * @param promotion The promotion to apply
     * @param quantity The quantity being purchased
     * @return The discounted price (total for all items)
     */
    public BigDecimal calculatePromotionalPrice(Promotion promotion, int quantity) {
        if (promotion == null || price == null) {
            return price != null ? price.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        }
        
        return promotion.calculateDiscountedPrice(price, quantity);
    }

    /**
     * Calculate savings from a promotion
     * @param promotion The promotion to apply
     * @param quantity The quantity being purchased
     * @return Amount saved
     */
    public BigDecimal calculateSavings(Promotion promotion, int quantity) {
        if (promotion == null || price == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal originalTotal = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal promotionalTotal = calculatePromotionalPrice(promotion, quantity);
        
        return originalTotal.subtract(promotionalTotal);
    }

    /**
     * Check if this item has any active promotions
     */
    public boolean hasActivePromotions() {
        return !getActivePromotions().isEmpty();
    }
}
