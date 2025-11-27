package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.ItemIngredient;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for ItemMenu management
 */
public interface ItemMenuService {

    /**
     * Find all menu items
     */
    List<ItemMenu> findAll();

    /**
     * Find all menu items ordered by name
     */
    List<ItemMenu> findAllOrderByName();

    /**
     * Find all menu items ordered by category and name
     */
    List<ItemMenu> findAllOrderByCategoryAndName();

    /**
     * Find menu item by ID
     */
    Optional<ItemMenu> findById(Long id);

    /**
     * Find menu item by name
     */
    Optional<ItemMenu> findByName(String name);

    /**
     * Find all menu items by category ID
     */
    List<ItemMenu> findByCategoryId(Long categoryId);

    /**
     * Find all active menu items by category ID
     */
    List<ItemMenu> findActiveByCategoryId(Long categoryId);

    /**
     * Find all active menu items
     */
    List<ItemMenu> findAllActive();

    /**
     * Find all available menu items (active and with enough stock)
     */
    List<ItemMenu> findAvailableItems();

    /**
     * Find items with low stock (active but unavailable)
     */
    List<ItemMenu> findItemsWithLowStock();

    /**
     * Search menu items by name
     */
    List<ItemMenu> searchByName(String searchTerm);

    /**
     * Find menu items by category and availability
     */
    List<ItemMenu> findByCategoryIdAndAvailability(Long categoryId, Boolean available);

    /**
     * Create a new menu item with its recipe
     */
    ItemMenu create(ItemMenu item, List<ItemIngredient> recipe);

    /**
     * Update an existing menu item and its recipe
     */
    ItemMenu update(Long id, ItemMenu item, List<ItemIngredient> recipe);

    /**
     * Activate a menu item
     */
    ItemMenu activate(Long id);

    /**
     * Deactivate a menu item
     */
    ItemMenu deactivate(Long id);

    /**
     * Delete a menu item (and its recipe)
     */
    void delete(Long id);

    // ========== Recipe Management ==========

    /**
     * Get all ingredients for a menu item (recipe)
     */
    List<ItemIngredient> getRecipe(Long itemMenuId);

    /**
     * Add an ingredient to a menu item's recipe
     */
    ItemIngredient addIngredientToRecipe(Long itemMenuId, ItemIngredient ingredient);

    /**
     * Remove an ingredient from a menu item's recipe
     */
    void removeIngredientFromRecipe(Long itemMenuId, Long ingredientId);

    /**
     * Update the entire recipe of a menu item
     */
    void updateRecipe(Long itemMenuId, List<ItemIngredient> newRecipe);

    /**
     * Clear all ingredients from a menu item's recipe
     */
    void clearRecipe(Long itemMenuId);

    // ========== Stock & Availability Management ==========

    /**
     * Check if there's enough stock to prepare a quantity of items
     */
    boolean hasEnoughStock(Long itemMenuId, int quantity);

    /**
     * Update the availability of a menu item based on current stock
     */
    void updateItemAvailability(Long itemMenuId);

    /**
     * Update availability for all active menu items
     */
    void updateAllItemsAvailability();

    // ========== Sales Methods (Ready but not used yet) ==========

    /**
     * Sell a menu item - deducts ingredients from stock
     * THIS METHOD IS READY BUT NOT USED YET - For future sales module
     */
    void sellItem(Long itemMenuId, int quantity);

    /**
     * Calculate the total cost of ingredients for a menu item
     */
    java.math.BigDecimal calculateIngredientsCost(Long itemMenuId);

    // ========== Statistics ==========

    /**
     * Count all menu items
     */
    long countAll();

    /**
     * Count active menu items
     */
    long countActive();

    /**
     * Count available menu items
     */
    long countAvailable();

    /**
     * Count unavailable menu items (active but no stock)
     */
    long countUnavailable();

    /**
     * Count menu items by category
     */
    long countByCategoryId(Long categoryId);

    /**
     * Count active menu items by category
     */
    long countActiveByCategoryId(Long categoryId);

    /**
     * Count available menu items by category
     */
    long countAvailableByCategoryId(Long categoryId);

    /**
     * Check if menu item exists by name
     */
    boolean existsByName(String name);

    /**
     * Check if menu item exists by name excluding a specific id
     */
    boolean existsByNameAndIdNot(String name, Long excludeId);

    /**
     * Find menu item by ID or throw exception
     */
    ItemMenu findByIdOrThrow(Long id);
}
