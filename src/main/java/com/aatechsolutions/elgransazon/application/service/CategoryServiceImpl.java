package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of CategoryService
 * Handles business logic for category operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        log.debug("Fetching all categories");
        return categoryRepository.findAllOrderedByDisplayOrder();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getAllActiveCategories() {
        log.debug("Fetching all active categories");
        return categoryRepository.findAllActiveOrderedByDisplayOrder();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryById(Long id) {
        log.debug("Fetching category with id: {}", id);
        return categoryRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryByName(String name) {
        log.debug("Fetching category with name: {}", name);
        return categoryRepository.findByName(name);
    }

    @Override
    public Category createCategory(Category category) {
        log.info("Creating new category: {}", category.getName());

        // Validate that category name doesn't already exist
        if (categoryRepository.existsByNameIgnoreCase(category.getName())) {
            log.error("Category name already exists: {}", category.getName());
            throw new IllegalArgumentException("Category with name '" + category.getName() + "' already exists");
        }

        // Set default values if not provided
        if (category.getActive() == null) {
            category.setActive(true);
        }

        Category savedCategory = categoryRepository.save(category);
        log.info("Category created successfully with id: {}", savedCategory.getIdCategory());
        return savedCategory;
    }

    @Override
    public Category updateCategory(Long id, Category category) {
        log.info("Updating category with id: {}", id);

        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Category not found with id: " + id);
                });

        // Check if name is being changed and if the new name already exists
        if (!existingCategory.getName().equalsIgnoreCase(category.getName())) {
            if (categoryRepository.existsByNameIgnoreCase(category.getName())) {
                log.error("Category name already exists: {}", category.getName());
                throw new IllegalArgumentException("Category with name '" + category.getName() + "' already exists");
            }
        }

        // Update fields
        existingCategory.setName(category.getName());
        existingCategory.setDescription(category.getDescription());
        existingCategory.setActive(category.getActive());
        existingCategory.setDisplayOrder(category.getDisplayOrder());
        existingCategory.setIcon(category.getIcon());

        Category updatedCategory = categoryRepository.save(existingCategory);
        log.info("Category updated successfully: {}", updatedCategory.getIdCategory());
        return updatedCategory;
    }

    @Override
    public void deleteCategory(Long id) {
        log.info("Soft deleting category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Category not found with id: " + id);
                });

        category.setActive(false);
        categoryRepository.save(category);
        log.info("Category soft deleted successfully: {}", id);
    }

    @Override
    public void permanentlyDeleteCategory(Long id) {
        log.warn("Permanently deleting category with id: {}", id);

        if (!categoryRepository.existsById(id)) {
            log.error("Category not found with id: {}", id);
            throw new IllegalArgumentException("Category not found with id: " + id);
        }

        categoryRepository.deleteById(id);
        log.info("Category permanently deleted: {}", id);
    }

    @Override
    public void activateCategory(Long id) {
        log.info("Activating category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Category not found with id: {}", id);
                    return new IllegalArgumentException("Category not found with id: " + id);
                });

        category.setActive(true);
        categoryRepository.save(category);
        log.info("Category activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean categoryNameExists(String name) {
        log.debug("Checking if category name exists: {}", name);
        return categoryRepository.existsByNameIgnoreCase(name);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveCategories() {
        log.debug("Counting active categories");
        return categoryRepository.countByActiveTrue();
    }
}
