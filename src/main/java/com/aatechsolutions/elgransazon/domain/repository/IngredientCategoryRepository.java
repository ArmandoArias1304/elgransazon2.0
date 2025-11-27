package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.IngredientCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for IngredientCategory entity
 */
@Repository
public interface IngredientCategoryRepository extends JpaRepository<IngredientCategory, Long> {

    /**
     * Find categories by name containing (case insensitive)
     */
    List<IngredientCategory> findByNameContainingIgnoreCase(String name);

    /**
     * Find categories by description containing (case insensitive)
     */
    List<IngredientCategory> findByDescriptionContainingIgnoreCase(String description);

    /**
     * Find all active categories ordered by name
     */
    List<IngredientCategory> findByActiveOrderByNameAsc(Boolean active);

    /**
     * Find all categories ordered by name
     */
    List<IngredientCategory> findAllByOrderByNameAsc();

    /**
     * Check if a category exists by name
     */
    boolean existsByName(String name);

    /**
     * Advanced search with multiple filters
     */
    @Query("SELECT c FROM IngredientCategory c " +
           "WHERE (:search IS NULL OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:active IS NULL OR c.active = :active) " +
           "ORDER BY c.name ASC")
    List<IngredientCategory> searchWithFilters(@Param("search") String search,
                                               @Param("active") Boolean active);

    /**
     * Count active categories
     */
    long countByActive(Boolean active);

    /**
     * Find all active categories (for dropdowns)
     */
    @Query("SELECT c FROM IngredientCategory c WHERE c.active = true ORDER BY c.name ASC")
    List<IngredientCategory> findAllActive();
}
