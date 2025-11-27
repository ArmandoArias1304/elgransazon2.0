package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Ingredient;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import com.aatechsolutions.elgransazon.domain.repository.IngredientCategoryRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementation for Ingredient management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientServiceImpl implements IngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientCategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> findAll() {
        log.info("Finding all ingredients");
        return ingredientRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ingredient> findById(Long id) {
        log.info("Finding ingredient by id: {}", id);
        return ingredientRepository.findById(id);
    }

    @Override
    @Transactional
    public Ingredient create(Ingredient ingredient) {
        log.info("Creating new ingredient: {}", ingredient.getName());

        // Validate unique name
        if (ingredientRepository.existsByName(ingredient.getName())) {
            log.error("Ingredient with name {} already exists", ingredient.getName());
            throw new IllegalArgumentException("Ya existe un ingrediente con el nombre: " + ingredient.getName());
        }

        // Validate category is required
        if (ingredient.getCategory() == null || ingredient.getCategory().getIdCategory() == null) {
            log.error("Category is required for ingredient");
            throw new IllegalArgumentException("Debe seleccionar una categoría válida");
        }

        // Validate category exists
        IngredientCategory category = categoryRepository.findById(ingredient.getCategory().getIdCategory())
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", ingredient.getCategory().getIdCategory());
                    return new IllegalArgumentException("La categoría seleccionada no existe");
                });

        ingredient.setCategory(category);

        Ingredient savedIngredient = ingredientRepository.save(ingredient);
        log.info("Ingredient created successfully with id: {}", savedIngredient.getIdIngredient());
        return savedIngredient;
    }

    @Override
    @Transactional
    public Ingredient update(Long id, Ingredient ingredientDetails) {
        log.info("Updating ingredient with id: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", id);
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + id);
                });

        // Validate unique name if changed
        if (!ingredient.getName().equals(ingredientDetails.getName()) &&
            ingredientRepository.existsByName(ingredientDetails.getName())) {
            log.error("Ingredient with name {} already exists", ingredientDetails.getName());
            throw new IllegalArgumentException("Ya existe un ingrediente con el nombre: " + ingredientDetails.getName());
        }

        // Validate category
        if (ingredientDetails.getCategory() == null || ingredientDetails.getCategory().getIdCategory() == null) {
            log.error("Category is required for ingredient");
            throw new IllegalArgumentException("Debe seleccionar una categoría válida");
        }

        IngredientCategory category = categoryRepository.findById(ingredientDetails.getCategory().getIdCategory())
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", ingredientDetails.getCategory().getIdCategory());
                    return new IllegalArgumentException("La categoría seleccionada no existe");
                });

        // Update fields
        ingredient.setName(ingredientDetails.getName());
        ingredient.setDescription(ingredientDetails.getDescription());
        ingredient.setCurrentStock(ingredientDetails.getCurrentStock());
        ingredient.setMinStock(ingredientDetails.getMinStock());
        ingredient.setMaxStock(ingredientDetails.getMaxStock());
        ingredient.setUnitOfMeasure(ingredientDetails.getUnitOfMeasure());
        ingredient.setCostPerUnit(ingredientDetails.getCostPerUnit());
        ingredient.setCurrency(ingredientDetails.getCurrency());
        ingredient.setStorageLocation(ingredientDetails.getStorageLocation());
        ingredient.setShelfLifeDays(ingredientDetails.getShelfLifeDays());
        ingredient.setActive(ingredientDetails.getActive());
        ingredient.setCategory(category);

        Ingredient updatedIngredient = ingredientRepository.save(ingredient);
        log.info("Ingredient updated successfully: {}", id);
        return updatedIngredient;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deactivating ingredient with id: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", id);
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + id);
                });

        ingredient.setActive(false);
        ingredientRepository.save(ingredient);
        log.info("Ingredient deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        log.info("Activating ingredient with id: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", id);
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + id);
                });

        ingredient.setActive(true);
        ingredientRepository.save(ingredient);
        log.info("Ingredient activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> searchWithAllFilters(String search, Long categoryId, Long supplierId, 
                                                 String sortBy, Boolean active) {
        log.info("Searching ingredients with filters - search: {}, categoryId: {}, supplierId: {}, sortBy: {}, active: {}",
                search, categoryId, supplierId, sortBy, active);

        // Normalize search string
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        List<Ingredient> ingredients;

        // If supplier filter is provided, get ingredients for that supplier through category
        if (supplierId != null) {
            ingredients = ingredientRepository.findBySupplier(supplierId);
            
            // Apply additional filters
            if (normalizedSearch != null) {
                final String searchLower = normalizedSearch.toLowerCase();
                ingredients = ingredients.stream()
                    .filter(i -> i.getName().toLowerCase().contains(searchLower) ||
                               (i.getDescription() != null && i.getDescription().toLowerCase().contains(searchLower)) ||
                               (i.getStorageLocation() != null && i.getStorageLocation().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
            }
            
            if (categoryId != null) {
                ingredients = ingredients.stream()
                    .filter(i -> i.getCategory() != null && i.getCategory().getIdCategory().equals(categoryId))
                    .collect(Collectors.toList());
            }
            
            if (active != null) {
                ingredients = ingredients.stream()
                    .filter(i -> i.getActive().equals(active))
                    .collect(Collectors.toList());
            }
        } else {
            // Normal search without supplier filter
            ingredients = ingredientRepository.searchWithFilters(normalizedSearch, categoryId, active);
        }

        // Apply sorting based on sortBy parameter
        if (sortBy != null && !sortBy.isEmpty()) {
            if ("stock-asc".equals(sortBy)) {
                // Sort by stock ascending (menor a mayor)
                ingredients.sort(Comparator.comparing(Ingredient::getCurrentStock));
            } else if ("stock-desc".equals(sortBy)) {
                // Sort by stock descending (mayor a menor)
                ingredients.sort(Comparator.comparing(Ingredient::getCurrentStock).reversed());
            } else {
                // Default: Sort alphabetically by name
                ingredients.sort(Comparator.comparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER));
            }
        } else {
            // Default: Sort alphabetically by name
            ingredients.sort(Comparator.comparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER));
        }

        log.info("Found {} ingredients with filters", ingredients.size());
        return ingredients;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> findByCategoryId(Long categoryId) {
        log.info("Finding ingredients for category ID: {}", categoryId);
        return ingredientRepository.findByCategoryIdCategoryOrderByNameAsc(categoryId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> getSuppliersForIngredient(Long ingredientId) {
        log.info("Getting suppliers for ingredient ID: {}", ingredientId);

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> {
                    log.error("Ingredient not found with id: {}", ingredientId);
                    return new IllegalArgumentException("Ingrediente no encontrado con id: " + ingredientId);
                });

        if (ingredient.getCategory() == null) {
            log.warn("Ingredient {} has no category, returning empty supplier list", ingredientId);
            return Collections.emptyList();
        }

        List<Supplier> suppliers = new ArrayList<>(ingredient.getCategory().getSuppliers());
        suppliers.sort(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER));

        log.info("Found {} suppliers for ingredient {}", suppliers.size(), ingredientId);
        return suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> getLowStockIngredients() {
        log.info("Finding low stock ingredients");
        return ingredientRepository.findLowStockIngredients();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ingredient> getOutOfStockIngredients() {
        log.info("Finding out of stock ingredients");
        return ingredientRepository.findOutOfStockIngredients();
    }

    @Override
    @Transactional(readOnly = true)
    public long countLowStock() {
        return ingredientRepository.countLowStockIngredients();
    }

    @Override
    @Transactional(readOnly = true)
    public long countOutOfStock() {
        return ingredientRepository.countOutOfStockIngredients();
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        return ingredientRepository.countByActive(true);
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        return ingredientRepository.countByActive(false);
    }
}
