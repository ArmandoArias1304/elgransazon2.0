package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.IngredientService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for ItemMenu (Menu Items) management
 * Accessible by ADMIN and MANAGER roles
 */
@Controller
@RequestMapping("/admin/menu-items")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
@RequiredArgsConstructor
@Slf4j
public class ItemMenuController {

    private final ItemMenuService itemMenuService;
    private final CategoryService categoryService;
    private final IngredientService ingredientService;

    /**
     * Show list of all menu items
     */
    @GetMapping
    public String listMenuItems(Model model) {
        log.debug("Displaying menu items list");

        List<ItemMenu> menuItems = itemMenuService.findAllOrderByCategoryAndName();
        List<Category> categories = categoryService.getAllCategories();

        long totalCount = itemMenuService.countAll();
        long activeCount = itemMenuService.countActive();
        long availableCount = itemMenuService.countAvailable();
        long unavailableCount = itemMenuService.countUnavailable();

        model.addAttribute("menuItems", menuItems);
        model.addAttribute("categories", categories);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("availableCount", availableCount);
        model.addAttribute("unavailableCount", unavailableCount);

        return "admin/menu-items/list";
    }

    /**
     * Show form to create a new menu item
     */
    @GetMapping("/new")
    public String newMenuItemForm(Model model) {
        log.debug("Displaying new menu item form");

        ItemMenu itemMenu = new ItemMenu();
        itemMenu.setActive(true);
        itemMenu.setAvailable(true);
        itemMenu.setRequiresPreparation(true); // ✅ Inicializar explícitamente

        List<Category> categories = categoryService.getAllActiveCategories();
        List<Ingredient> ingredients = ingredientService.findAll();

        model.addAttribute("itemMenu", itemMenu);
        model.addAttribute("categories", categories);
        model.addAttribute("ingredients", ingredients);
        model.addAttribute("recipe", new ArrayList<ItemIngredient>());
        model.addAttribute("formAction", "/admin/menu-items");

        return "admin/menu-items/form";
    }

    /**
     * Show form to edit an existing menu item
     */
    @GetMapping("/edit/{id}")
    public String editMenuItemForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Displaying edit form for menu item ID: {}", id);

        return itemMenuService.findById(id)
                .map(itemMenu -> {
                    List<Category> categories = categoryService.getAllCategories();
                    List<Ingredient> ingredients = ingredientService.findAll();
                    List<ItemIngredient> recipe = itemMenuService.getRecipe(id);

                    model.addAttribute("itemMenu", itemMenu);
                    model.addAttribute("categories", categories);
                    model.addAttribute("ingredients", ingredients);
                    model.addAttribute("recipe", recipe);
                    model.addAttribute("formAction", "/admin/menu-items/" + id);
                    return "admin/menu-items/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Item del menú no encontrado");
                    return "redirect:/admin/menu-items";
                });
    }

    /**
     * Create a new menu item with recipe
     */
    @PostMapping
    public String createMenuItem(
            @Valid @ModelAttribute("itemMenu") ItemMenu itemMenu,
            BindingResult bindingResult,
            @RequestParam(value = "ingredientIds", required = false) List<Long> ingredientIds,
            @RequestParam(value = "quantities", required = false) List<BigDecimal> quantities,
            @RequestParam(value = "units", required = false) List<String> units,
            @RequestParam(value = "requiresPreparation", required = false) Boolean requiresPreparationParam,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Creating new menu item: {}", itemMenu.getName());
        log.info("🔍 requiresPreparation received from form: {}", itemMenu.getRequiresPreparation());
        log.info("🔍 requiresPreparation as @RequestParam: {}", requiresPreparationParam);
        
        // Si el parámetro está presente, usarlo (para debug)
        if (requiresPreparationParam != null) {
            log.info("🔍 Using @RequestParam value: {}", requiresPreparationParam);
            itemMenu.setRequiresPreparation(requiresPreparationParam);
        }

        if (bindingResult.hasErrors()) {
            loadFormData(model, itemMenu, new ArrayList<>());
            return "admin/menu-items/form";
        }

        try {
            // Build recipe from form data
            List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);

            // Validate at least one ingredient
            if (recipe.isEmpty()) {
                model.addAttribute("errorMessage", "Debe agregar al menos un ingrediente a la receta");
                loadFormData(model, itemMenu, recipe);
                return "admin/menu-items/form";
            }

            ItemMenu created = itemMenuService.create(itemMenu, recipe);

            log.info("Menu item created successfully with ID: {}", created.getIdItemMenu());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item del menú '" + created.getName() + "' creado exitosamente");
            return "redirect:/admin/menu-items";

        } catch (IllegalArgumentException e) {
            log.error("Validation error creating menu item: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);
            loadFormData(model, itemMenu, recipe);
            return "admin/menu-items/form";

        } catch (Exception e) {
            log.error("Error creating menu item", e);
            model.addAttribute("errorMessage", "Error al crear el item del menú: " + e.getMessage());
            List<ItemIngredient> recipe = buildRecipe(ingredientIds, quantities, units);
            loadFormData(model, itemMenu, recipe);
            return "admin/menu-items/form";
        }
    }

    /**
     * Update an existing menu item and its recipe
     */
    @PostMapping("/{id}")
    public String updateMenuItem(
            @PathVariable Long id,
            @Valid @ModelAttribute("itemMenu") ItemMenu itemMenu,
            BindingResult bindingResult,
            @RequestParam(value = "ingredientIds", required = false) List<Long> ingredientIds,
            @RequestParam(value = "quantities", required = false) List<String> quantities,
            @RequestParam(value = "units", required = false) List<String> units,
            @RequestParam(value = "requiresPreparation", required = false) Boolean requiresPreparationParam,
            Model model,
            RedirectAttributes redirectAttributes) {

        log.info("Updating menu item with ID: {}", id);
        log.info("🔍 requiresPreparation received from form: {}", itemMenu.getRequiresPreparation());
        log.info("🔍 requiresPreparation as @RequestParam: {}", requiresPreparationParam);
        
        // Si el parámetro está presente, usarlo (para debug)
        if (requiresPreparationParam != null) {
            log.info("🔍 Using @RequestParam value: {}", requiresPreparationParam);
            itemMenu.setRequiresPreparation(requiresPreparationParam);
        }

        if (bindingResult.hasErrors()) {
            loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
            model.addAttribute("formAction", "/admin/menu-items/" + id);
            return "admin/menu-items/form";
        }

        try {
            // Build recipe from form data
            List<ItemIngredient> recipe = null;
            if (ingredientIds != null && !ingredientIds.isEmpty()) {
                // Convert String quantities to BigDecimal safely
                List<BigDecimal> quantitiesBD = new ArrayList<>();
                if (quantities != null) {
                    for (String qty : quantities) {
                        try {
                            if (qty != null && !qty.trim().isEmpty()) {
                                quantitiesBD.add(new BigDecimal(qty));
                            } else {
                                quantitiesBD.add(BigDecimal.ZERO);
                            }
                        } catch (NumberFormatException e) {
                            log.warn("Invalid quantity format: {}", qty);
                            quantitiesBD.add(BigDecimal.ZERO);
                        }
                    }
                }
                recipe = buildRecipe(ingredientIds, quantitiesBD, units);
            }

            // Validate at least one ingredient
            if (recipe == null || recipe.isEmpty()) {
                model.addAttribute("errorMessage", "Debe agregar al menos un ingrediente a la receta");
                loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
                model.addAttribute("formAction", "/admin/menu-items/" + id);
                return "admin/menu-items/form";
            }

            ItemMenu updated = itemMenuService.update(id, itemMenu, recipe);

            log.info("Menu item updated successfully: {}", updated.getIdItemMenu());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item del menú '" + updated.getName() + "' actualizado exitosamente");
            return "redirect:/admin/menu-items";

        } catch (IllegalArgumentException e) {
            log.error("Validation error updating menu item: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", e.getMessage());
            loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
            model.addAttribute("formAction", "/admin/menu-items/" + id);
            return "admin/menu-items/form";

        } catch (Exception e) {
            log.error("Error updating menu item: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Error al actualizar el item del menú: " + e.getMessage());
            loadFormData(model, itemMenu, itemMenuService.getRecipe(id));
            model.addAttribute("formAction", "/admin/menu-items/" + id);
            return "admin/menu-items/form";
        }
    }

    /**
     * Activate a menu item
     */
    @PostMapping("/{id}/activate")
    public String activateMenuItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Activating menu item with ID: {}", id);

        try {
            ItemMenu item = itemMenuService.activate(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item '" + item.getName() + "' activado exitosamente");
        } catch (Exception e) {
            log.error("Error activating menu item", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al activar el item: " + e.getMessage());
        }

        return "redirect:/admin/menu-items";
    }

    /**
     * Deactivate a menu item
     */
    @PostMapping("/{id}/deactivate")
    public String deactivateMenuItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deactivating menu item with ID: {}", id);

        try {
            ItemMenu item = itemMenuService.deactivate(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Item '" + item.getName() + "' desactivado exitosamente");
        } catch (Exception e) {
            log.error("Error deactivating menu item", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al desactivar el item: " + e.getMessage());
        }

        return "redirect:/admin/menu-items";
    }

    /**
     * Delete a menu item
     */
    @PostMapping("/{id}/delete")
    public String deleteMenuItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Deleting menu item with ID: {}", id);

        try {
            itemMenuService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Item del menú eliminado exitosamente");
        } catch (Exception e) {
            log.error("Error deleting menu item", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al eliminar el item: " + e.getMessage());
        }

        return "redirect:/admin/menu-items";
    }

    // ========== AJAX Endpoints ==========

    /**
     * Get ingredient details by ID (AJAX)
     * Returns unit of measure and other relevant info
     */
    @GetMapping("/ingredient/{ingredientId}")
    @ResponseBody
    public Map<String, Object> getIngredientDetails(@PathVariable Long ingredientId) {
        log.debug("Fetching ingredient details for ID: {}", ingredientId);
        
        Map<String, Object> response = new HashMap<>();
        try {
            Ingredient ingredient = ingredientService.findById(ingredientId)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));
            
            response.put("success", true);
            response.put("id", ingredient.getIdIngredient());
            response.put("name", ingredient.getName());
            response.put("unitOfMeasure", ingredient.getUnitOfMeasure());
            response.put("currentStock", ingredient.getCurrentStock());
            response.put("costPerUnit", ingredient.getCostPerUnit());
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    /**
     * Get recipe for a menu item (AJAX)
     */
    @GetMapping("/{id}/recipe")
    @ResponseBody
    public List<Map<String, Object>> getRecipe(@PathVariable Long id) {
        log.debug("Fetching recipe for menu item ID: {}", id);
        
        List<ItemIngredient> recipe = itemMenuService.getRecipe(id);
        List<Map<String, Object>> response = new ArrayList<>();
        
        for (ItemIngredient item : recipe) {
            Map<String, Object> ingredientData = new HashMap<>();
            ingredientData.put("id", item.getIdItemIngredient());
            ingredientData.put("ingredientId", item.getIngredient().getIdIngredient());
            ingredientData.put("ingredientName", item.getIngredient().getName());
            ingredientData.put("quantity", item.getQuantity());
            ingredientData.put("unit", item.getUnit());
            ingredientData.put("currentStock", item.getIngredient().getCurrentStock());
            response.add(ingredientData);
        }
        
        return response;
    }

    /**
     * Check stock availability (AJAX)
     */
    @GetMapping("/{id}/check-stock")
    @ResponseBody
    public Map<String, Object> checkStock(@PathVariable Long id, @RequestParam int quantity) {
        log.debug("Checking stock for menu item {} quantity {}", id, quantity);
        
        Map<String, Object> response = new HashMap<>();
        try {
            boolean hasStock = itemMenuService.hasEnoughStock(id, quantity);
            response.put("hasStock", hasStock);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    /**
     * Get item cost calculation (AJAX)
     */
    @GetMapping("/{id}/cost")
    @ResponseBody
    public Map<String, Object> getItemCost(@PathVariable Long id) {
        log.debug("Calculating cost for menu item ID: {}", id);
        
        Map<String, Object> response = new HashMap<>();
        try {
            ItemMenu item = itemMenuService.findByIdOrThrow(id);
            BigDecimal cost = itemMenuService.calculateIngredientsCost(id);
            BigDecimal profitMargin = item.calculateProfitMarginPercentage();
            
            response.put("cost", cost);
            response.put("price", item.getPrice());
            response.put("profitMargin", profitMargin);
            response.put("success", true);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    // ========== Helper Methods ==========

    /**
     * Build recipe from form parameters
     */
    private List<ItemIngredient> buildRecipe(List<Long> ingredientIds, 
                                               List<BigDecimal> quantities, 
                                               List<String> units) {
        List<ItemIngredient> recipe = new ArrayList<>();
        
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return recipe;
        }
        
        for (int i = 0; i < ingredientIds.size(); i++) {
            Long ingredientId = ingredientIds.get(i);
            BigDecimal quantity = quantities != null && i < quantities.size() ? quantities.get(i) : BigDecimal.ZERO;
            String unit = units != null && i < units.size() ? units.get(i) : "";
            
            if (ingredientId != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
                Ingredient ingredient = new Ingredient();
                ingredient.setIdIngredient(ingredientId);
                
                ItemIngredient itemIngredient = ItemIngredient.builder()
                        .ingredient(ingredient)
                        .quantity(quantity)
                        .unit(unit)
                        .build();
                
                recipe.add(itemIngredient);
            }
        }
        
        return recipe;
    }

    /**
     * Load common form data
     */
    private void loadFormData(Model model, ItemMenu itemMenu, List<ItemIngredient> recipe) {
        List<Category> categories = categoryService.getAllCategories();
        List<Ingredient> ingredients = ingredientService.findAll();

        model.addAttribute("itemMenu", itemMenu);
        model.addAttribute("categories", categories);
        model.addAttribute("ingredients", ingredients);
        model.addAttribute("recipe", recipe);
        model.addAttribute("formAction", itemMenu.getIdItemMenu() != null ? 
                "/admin/menu-items/" + itemMenu.getIdItemMenu() : "/admin/menu-items");
    }
}
