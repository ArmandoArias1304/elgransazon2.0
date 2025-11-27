package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for Ingredient management
 */
public interface IngredientService {

    /**
     * Find all ingredients ordered by name
     */
    List<Ingredient> findAll();

    /**
     * Find ingredient by ID
     */
    Optional<Ingredient> findById(Long id);

    /**
     * Create a new ingredient
     */
    Ingredient create(Ingredient ingredient);

    /**
     * Update an existing ingredient
     */
    Ingredient update(Long id, Ingredient ingredient);

    /**
     * Soft delete (deactivate) an ingredient
     */
    void delete(Long id);

    /**
     * Activate an ingredient
     */
    void activate(Long id);

    /**
     * Search ingredients with all filters and sorting
     */
    List<Ingredient> searchWithAllFilters(String search, Long categoryId, Long supplierId, String sortBy, Boolean active);

    /**
     * Find ingredients by category
     */
    List<Ingredient> findByCategoryId(Long categoryId);

    /**
     * Get suppliers for a specific ingredient (through category)
     */
    List<Supplier> getSuppliersForIngredient(Long ingredientId);

    /**
     * Get ingredients with low stock
     */
    List<Ingredient> getLowStockIngredients();

    /**
     * Get ingredients out of stock
     */
    List<Ingredient> getOutOfStockIngredients();

    /**
     * Count low stock ingredients
     */
    long countLowStock();

    /**
     * Count out of stock ingredients
     */
    long countOutOfStock();

    /**
     * Get active ingredient count
     */
    long getActiveCount();

    /**
     * Get inactive ingredient count
     */
    long getInactiveCount();
}
