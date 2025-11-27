package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Category entity
 * Provides CRUD operations and custom queries for categories
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Find a category by its name
     * @param name the category name
     * @return Optional containing the category if found
     */
    Optional<Category> findByName(String name);

    /**
     * Find all active categories ordered by display order
     * @return List of active categories
     */
    @Query("SELECT c FROM Category c WHERE c.active = true ORDER BY c.displayOrder ASC, c.name ASC")
    List<Category> findAllActiveOrderedByDisplayOrder();

    /**
     * Find all categories ordered by display order
     * @return List of all categories
     */
    @Query("SELECT c FROM Category c ORDER BY c.displayOrder ASC, c.name ASC")
    List<Category> findAllOrderedByDisplayOrder();

    /**
     * Check if a category name already exists (case-insensitive)
     * @param name the category name to check
     * @return true if exists, false otherwise
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Count active categories
     * @return number of active categories
     */
    long countByActiveTrue();
}
