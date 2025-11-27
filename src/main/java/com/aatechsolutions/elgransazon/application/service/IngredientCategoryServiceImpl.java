package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.IngredientCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service implementation for IngredientCategory management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientCategoryServiceImpl implements IngredientCategoryService {

    private final IngredientCategoryRepository categoryRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<IngredientCategory> findAll() {
        log.info("Finding all ingredient categories");
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientCategory> findAllActive() {
        log.info("Finding all active ingredient categories");
        return categoryRepository.findAllActive();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngredientCategory> findById(Long id) {
        log.info("Finding ingredient category by id: {}", id);
        return categoryRepository.findById(id);
    }

    @Override
    @Transactional
    public IngredientCategory create(IngredientCategory category) {
        log.info("Creating new ingredient category: {}", category.getName());

        // Validate unique name
        if (categoryRepository.existsByName(category.getName())) {
            log.error("Category with name {} already exists", category.getName());
            throw new IllegalArgumentException("Ya existe una categoría con el nombre: " + category.getName());
        }

        // Set created by (authenticated user)
        setCreatedBy(category);

        IngredientCategory savedCategory = categoryRepository.save(category);
        log.info("Ingredient category created successfully with id: {}", savedCategory.getIdCategory());
        return savedCategory;
    }

    @Override
    @Transactional
    public IngredientCategory update(Long id, IngredientCategory categoryDetails) {
        log.info("Updating ingredient category with id: {}", id);

        IngredientCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Categoría no encontrada con id: " + id);
                });

        // Validate unique name if changed
        if (!category.getName().equals(categoryDetails.getName()) &&
            categoryRepository.existsByName(categoryDetails.getName())) {
            log.error("Category with name {} already exists", categoryDetails.getName());
            throw new IllegalArgumentException("Ya existe una categoría con el nombre: " + categoryDetails.getName());
        }

        // Update fields
        category.setName(categoryDetails.getName());
        category.setDescription(categoryDetails.getDescription());
        category.setIcon(categoryDetails.getIcon());
        category.setColor(categoryDetails.getColor());
        category.setActive(categoryDetails.getActive());

        // Update suppliers (ManyToMany relationship)
        category.getSuppliers().clear();
        if (categoryDetails.getSuppliers() != null) {
            category.getSuppliers().addAll(categoryDetails.getSuppliers());
        }

        IngredientCategory updatedCategory = categoryRepository.save(category);
        log.info("Ingredient category updated successfully: {}", id);
        return updatedCategory;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deactivating ingredient category with id: {}", id);

        IngredientCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Categoría no encontrada con id: " + id);
                });

        category.setActive(false);
        categoryRepository.save(category);
        log.info("Ingredient category deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        log.info("Activating ingredient category with id: {}", id);

        IngredientCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Categoría no encontrada con id: " + id);
                });

        category.setActive(true);
        categoryRepository.save(category);
        log.info("Ingredient category activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientCategory> searchWithFilters(String search, Boolean active) {
        log.info("Searching ingredient categories with filters - search: {}, active: {}", search, active);

        // Normalize search string
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        List<IngredientCategory> categories = categoryRepository.searchWithFilters(normalizedSearch, active);
        log.info("Found {} categories with filters", categories.size());
        return categories;
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        return categoryRepository.countByActive(true);
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        return categoryRepository.countByActive(false);
    }

    /**
     * Set the created by field with the authenticated user
     */
    private void setCreatedBy(IngredientCategory category) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

                String username = authentication.getName();
                Optional<Employee> employee = employeeRepository.findByUsername(username);

                employee.ifPresent(category::setCreatedBy);
            }
        } catch (Exception e) {
            log.warn("Could not set created by: {}", e.getMessage());
        }
    }
}
