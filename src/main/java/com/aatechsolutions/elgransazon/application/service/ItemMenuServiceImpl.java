package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of ItemMenuService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ItemMenuServiceImpl implements ItemMenuService {

    private final ItemMenuRepository itemMenuRepository;
    private final ItemIngredientRepository itemIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public List<ItemMenu> findAll() {
        log.debug("Fetching all menu items");
        return itemMenuRepository.findAll();
    }

    @Override
    public List<ItemMenu> findAllOrderByName() {
        log.debug("Fetching all menu items ordered by name");
        return itemMenuRepository.findAllOrderByName();
    }

    @Override
    @Transactional
    public List<ItemMenu> findAllOrderByCategoryAndName() {
        log.debug("Fetching all menu items ordered by category and name");
        List<ItemMenu> items = itemMenuRepository.findAllOrderByCategoryAndName();
        
        // Update availability for all items based on current ingredient stock
        for (ItemMenu item : items) {
            item.updateAvailability();
        }
        
        // Save updated availability status
        itemMenuRepository.saveAll(items);
        
        return items;
    }

    @Override
    public Optional<ItemMenu> findById(Long id) {
        log.debug("Finding menu item by ID: {}", id);
        return itemMenuRepository.findById(id);
    }

    @Override
    public Optional<ItemMenu> findByName(String name) {
        log.debug("Finding menu item by name: {}", name);
        return itemMenuRepository.findByName(name);
    }

    @Override
    public List<ItemMenu> findByCategoryId(Long categoryId) {
        log.debug("Finding menu items by category ID: {}", categoryId);
        return itemMenuRepository.findByCategoryId(categoryId);
    }

    @Override
    public List<ItemMenu> findActiveByCategoryId(Long categoryId) {
        log.debug("Finding active menu items by category ID: {}", categoryId);
        return itemMenuRepository.findActiveByCategoryId(categoryId);
    }

    @Override
    public List<ItemMenu> findAllActive() {
        log.debug("Finding all active menu items");
        return itemMenuRepository.findByActiveTrue();
    }

    @Override
    public List<ItemMenu> findAvailableItems() {
        log.debug("Finding all available menu items (including out of stock)");
        // Changed: Now returns ALL active items, regardless of stock
        // Frontend will show "AGOTADO" badge for items without stock
        return itemMenuRepository.findByActiveTrue();
    }

    @Override
    public List<ItemMenu> findItemsWithLowStock() {
        log.debug("Finding menu items with low stock");
        return itemMenuRepository.findItemsWithLowStock();
    }

    @Override
    public List<ItemMenu> searchByName(String searchTerm) {
        log.debug("Searching menu items by name: {}", searchTerm);
        return itemMenuRepository.searchByName(searchTerm);
    }

    @Override
    public List<ItemMenu> findByCategoryIdAndAvailability(Long categoryId, Boolean available) {
        log.debug("Finding menu items by category {} and availability {}", categoryId, available);
        return itemMenuRepository.findByCategoryIdAndAvailability(categoryId, available);
    }

    @Override
    @Transactional
    public ItemMenu create(ItemMenu item, List<ItemIngredient> recipe) {
        log.info("Creating new menu item: {}", item.getName());
        log.info("🔍 requiresPreparation value before save: {}", item.getRequiresPreparation());

        // Validate unique name
        if (itemMenuRepository.existsByName(item.getName())) {
            throw new IllegalArgumentException("Ya existe un item del menú con el nombre: " + item.getName());
        }

        // Validate and load category
        if (item.getCategory() == null || item.getCategory().getIdCategory() == null) {
            throw new IllegalArgumentException("La categoría es requerida");
        }
        Category category = categoryRepository.findById(item.getCategory().getIdCategory())
                .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));

        // Set relationships
        item.setCategory(category);

        // Set timestamps
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        // Set defaults
        if (item.getActive() == null) {
            item.setActive(true);
        }
        if (item.getAvailable() == null) {
            item.setAvailable(true);
        }

        // Save the item first
        ItemMenu saved = itemMenuRepository.save(item);
        log.info("Menu item created with ID: {}", saved.getIdItemMenu());
        log.info("🔍 requiresPreparation value after save: {}", saved.getRequiresPreparation());

        // Save the recipe if provided
        if (recipe != null && !recipe.isEmpty()) {
            log.info("Saving recipe with {} ingredients", recipe.size());
            for (ItemIngredient ingredient : recipe) {
                // Validate ingredient exists
                if (ingredient.getIngredient() == null || ingredient.getIngredient().getIdIngredient() == null) {
                    throw new IllegalArgumentException("ID de ingrediente inválido en la receta");
                }
                
                Ingredient ing = ingredientRepository.findById(ingredient.getIngredient().getIdIngredient())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Ingrediente no encontrado con ID: " + ingredient.getIngredient().getIdIngredient()));
                
                ingredient.setIngredient(ing);
                ingredient.setItemMenu(saved);
                ingredient.setCreatedAt(LocalDateTime.now());
                
                itemIngredientRepository.save(ingredient);
            }
        }

        // Update availability based on stock
        updateItemAvailability(saved.getIdItemMenu());

        return saved;
    }

    @Override
    @Transactional
    public ItemMenu update(Long id, ItemMenu item, List<ItemIngredient> recipe) {
        log.info("Updating menu item with ID: {}", id);
        log.info("🔍 requiresPreparation value received: {}", item.getRequiresPreparation());

        ItemMenu existing = findByIdOrThrow(id);

        // Validate unique name if changed
        if (!existing.getName().equals(item.getName()) && 
            itemMenuRepository.existsByName(item.getName())) {
            throw new IllegalArgumentException("Ya existe un item del menú con el nombre: " + item.getName());
        }

        // Validate and load category if changed
        if (item.getCategory() != null && item.getCategory().getIdCategory() != null) {
            Category category = categoryRepository.findById(item.getCategory().getIdCategory())
                    .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));
            existing.setCategory(category);
        }

        // Update fields
        existing.setName(item.getName());
        existing.setDescription(item.getDescription());
        existing.setPrice(item.getPrice());
        existing.setImageUrl(item.getImageUrl());
        existing.setActive(item.getActive());
        existing.setRequiresPreparation(item.getRequiresPreparation()); // ✅ AGREGADO
        existing.setUpdatedAt(LocalDateTime.now());

        ItemMenu updated = itemMenuRepository.save(existing);
        log.info("🔍 requiresPreparation value after save: {}", updated.getRequiresPreparation());

        // Update recipe if provided
        if (recipe != null) {
            updateRecipe(id, recipe);
        }

        log.info("Menu item updated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public ItemMenu activate(Long id) {
        log.info("Activating menu item with ID: {}", id);

        ItemMenu item = findByIdOrThrow(id);
        item.setActive(true);
        item.setUpdatedAt(LocalDateTime.now());

        // Update availability
        updateItemAvailability(id);

        ItemMenu updated = itemMenuRepository.save(item);
        log.info("Menu item activated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public ItemMenu deactivate(Long id) {
        log.info("Deactivating menu item with ID: {}", id);

        ItemMenu item = findByIdOrThrow(id);
        item.setActive(false);
        item.setUpdatedAt(LocalDateTime.now());

        ItemMenu updated = itemMenuRepository.save(item);
        log.info("Menu item deactivated successfully: {}", id);
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting menu item with ID: {}", id);

        ItemMenu item = findByIdOrThrow(id);
        
        // Recipe will be deleted automatically due to CASCADE
        itemMenuRepository.delete(item);
        
        log.info("Menu item deleted successfully: {}", id);
    }

    // ========== Recipe Management ==========

    @Override
    public List<ItemIngredient> getRecipe(Long itemMenuId) {
        log.debug("Getting recipe for menu item ID: {}", itemMenuId);
        return itemIngredientRepository.findByItemMenuId(itemMenuId);
    }

    @Override
    @Transactional
    public ItemIngredient addIngredientToRecipe(Long itemMenuId, ItemIngredient ingredient) {
        log.info("Adding ingredient to recipe of menu item ID: {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        
        // Validate ingredient exists
        Ingredient ing = ingredientRepository.findById(ingredient.getIngredient().getIdIngredient())
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));

        // Check if ingredient already exists in recipe
        Optional<ItemIngredient> existing = itemIngredientRepository.findByItemMenuIdAndIngredientId(
            itemMenuId, ing.getIdIngredient());
        
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                "El ingrediente '" + ing.getName() + "' ya está en la receta");
        }

        ingredient.setItemMenu(item);
        ingredient.setIngredient(ing);
        ingredient.setCreatedAt(LocalDateTime.now());

        ItemIngredient saved = itemIngredientRepository.save(ingredient);
        
        // Update item availability
        updateItemAvailability(itemMenuId);
        
        log.info("Ingredient added to recipe successfully");
        return saved;
    }

    @Override
    @Transactional
    public void removeIngredientFromRecipe(Long itemMenuId, Long ingredientId) {
        log.info("Removing ingredient {} from recipe of menu item {}", ingredientId, itemMenuId);

        ItemIngredient itemIngredient = itemIngredientRepository
                .findByItemMenuIdAndIngredientId(itemMenuId, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado en la receta"));

        itemIngredientRepository.delete(itemIngredient);
        
        // Update item availability
        updateItemAvailability(itemMenuId);
        
        log.info("Ingredient removed from recipe successfully");
    }

    @Override
    @Transactional
    public void updateRecipe(Long itemMenuId, List<ItemIngredient> newRecipe) {
        log.info("Updating entire recipe for menu item ID: {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);

        // Delete old recipe
        List<ItemIngredient> oldRecipe = itemIngredientRepository.findByItemMenuId(itemMenuId);
        if (!oldRecipe.isEmpty()) {
            itemIngredientRepository.deleteAll(oldRecipe);
            // Force flush to execute DELETE before INSERT to avoid constraint violations
            itemIngredientRepository.flush();
            log.debug("Deleted {} old recipe items", oldRecipe.size());
        }

        // Save new recipe
        if (newRecipe != null && !newRecipe.isEmpty()) {
            for (ItemIngredient ingredient : newRecipe) {
                Ingredient ing = ingredientRepository.findById(ingredient.getIngredient().getIdIngredient())
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Ingrediente no encontrado con ID: " + ingredient.getIngredient().getIdIngredient()));
                
                ingredient.setIngredient(ing);
                ingredient.setItemMenu(item);
                ingredient.setCreatedAt(LocalDateTime.now());
                
                itemIngredientRepository.save(ingredient);
            }
            log.debug("Saved {} new recipe items", newRecipe.size());
        }

        // Update availability
        updateItemAvailability(itemMenuId);
        
        log.info("Recipe updated successfully");
    }

    @Override
    @Transactional
    public void clearRecipe(Long itemMenuId) {
        log.info("Clearing recipe for menu item ID: {}", itemMenuId);

        List<ItemIngredient> recipe = itemIngredientRepository.findByItemMenuId(itemMenuId);
        itemIngredientRepository.deleteAll(recipe);
        
        log.info("Recipe cleared successfully");
    }

    // ========== Stock & Availability Management ==========

    @Override
    public boolean hasEnoughStock(Long itemMenuId, int quantity) {
        log.debug("Checking stock for menu item {} quantity {}", itemMenuId, quantity);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        return item.hasEnoughStock(quantity);
    }

    @Override
    @Transactional
    public void updateItemAvailability(Long itemMenuId) {
        log.debug("Updating availability for menu item ID: {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        item.updateAvailability(); // Method in entity
        itemMenuRepository.save(item);
    }

    @Override
    @Transactional
    public void updateAllItemsAvailability() {
        log.info("Updating availability for all active menu items");

        List<ItemMenu> allItems = itemMenuRepository.findByActiveTrue();
        for (ItemMenu item : allItems) {
            item.updateAvailability();
        }
        itemMenuRepository.saveAll(allItems);
        
        log.info("Updated availability for {} items", allItems.size());
    }

    // ========== Sales Methods (Ready but not used yet) ==========

    /**
     * ⭐ MÉTODO PARA VENTAS - LISTO PERO NO SE USA AÚN
     * Este método será usado por el módulo de ventas en el futuro
     */
    @Override
    @Transactional
    public void sellItem(Long itemMenuId, int quantity) {
        log.info("Processing sale: Menu item {} x {} units", itemMenuId, quantity);

        // 1. Get the item
        ItemMenu item = findByIdOrThrow(itemMenuId);
        
        // 2. Validate item is active and available
        if (!item.getActive()) {
            throw new IllegalStateException("El item no está activo: " + item.getName());
        }
        if (!item.getAvailable()) {
            throw new IllegalStateException("El item no está disponible: " + item.getName());
        }
        
        // 3. Verify enough stock
        if (!hasEnoughStock(itemMenuId, quantity)) {
            throw new IllegalStateException(
                "No hay ingredientes suficientes para preparar " + quantity + " " + item.getName());
        }
        
        // 4. Deduct ingredients from stock
        List<ItemIngredient> recipe = getRecipe(itemMenuId);
        for (ItemIngredient itemIngredient : recipe) {
            try {
                // Deduct from stock (method in ItemIngredient entity)
                BigDecimal newStock = itemIngredient.deductFromStock(quantity);
                
                // Save the ingredient with the new stock
                ingredientRepository.save(itemIngredient.getIngredient());
                
                log.info("Deducted {} {} of '{}'. New stock: {}", 
                         itemIngredient.getQuantity().multiply(BigDecimal.valueOf(quantity)),
                         itemIngredient.getUnit(),
                         itemIngredient.getIngredientName(),
                         newStock);
                         
            } catch (Exception e) {
                log.error("Error deducting ingredient: {}", e.getMessage());
                throw new RuntimeException("Error al descontar del inventario: " + e.getMessage());
            }
        }
        
        // 5. Update item availability
        updateItemAvailability(itemMenuId);
        
        log.info("Sale processed successfully: {} x {} units", item.getName(), quantity);
    }

    @Override
    public BigDecimal calculateIngredientsCost(Long itemMenuId) {
        log.debug("Calculating ingredients cost for menu item ID: {}", itemMenuId);

        ItemMenu item = findByIdOrThrow(itemMenuId);
        return item.calculateIngredientsCost();
    }

    // ========== Statistics ==========

    @Override
    public long countAll() {
        return itemMenuRepository.count();
    }

    @Override
    public long countActive() {
        return itemMenuRepository.findByActiveTrue().size();
    }

    @Override
    public long countAvailable() {
        return itemMenuRepository.countAvailable();
    }

    @Override
    public long countUnavailable() {
        return itemMenuRepository.countUnavailable();
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return itemMenuRepository.countByCategoryId(categoryId);
    }

    @Override
    public long countActiveByCategoryId(Long categoryId) {
        return itemMenuRepository.countActiveByCategoryId(categoryId);
    }

    @Override
    public long countAvailableByCategoryId(Long categoryId) {
        return itemMenuRepository.countAvailableByCategoryId(categoryId);
    }

    @Override
    public boolean existsByName(String name) {
        return itemMenuRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long excludeId) {
        return itemMenuRepository.existsByNameAndIdNot(name, excludeId);
    }

    @Override
    public ItemMenu findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item del menú no encontrado con ID: " + id));
    }
}
