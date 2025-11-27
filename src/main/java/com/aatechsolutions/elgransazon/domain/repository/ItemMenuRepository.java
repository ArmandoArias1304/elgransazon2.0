package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ItemMenu entity
 */
@Repository
public interface ItemMenuRepository extends JpaRepository<ItemMenu, Long> {

    /**
     * Find item by name
     */
    Optional<ItemMenu> findByName(String name);

    /**
     * Find all items by category ID
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Find all active items by category ID
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true ORDER BY i.name ASC")
    List<ItemMenu> findActiveByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Find all active items
     */
    List<ItemMenu> findByActiveTrue();

    /**
     * Find all available items (active and available)
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.active = true AND i.available = true ORDER BY i.name ASC")
    List<ItemMenu> findAvailableItems();

    /**
     * Find all items ordered by name
     */
    @Query("SELECT i FROM ItemMenu i ORDER BY i.name ASC")
    List<ItemMenu> findAllOrderByName();

    /**
     * Find all items ordered by category and name
     */
    @Query("SELECT i FROM ItemMenu i ORDER BY i.category.name ASC, i.name ASC")
    List<ItemMenu> findAllOrderByCategoryAndName();

    /**
     * Check if item name exists
     */
    boolean existsByName(String name);

    /**
     * Check if item name exists excluding a specific id (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM ItemMenu i " +
           "WHERE i.name = :name AND i.idItemMenu <> :excludeId")
    boolean existsByNameAndIdNot(@Param("name") String name, @Param("excludeId") Long excludeId);

    /**
     * Count items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId")
    long countByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count active items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true")
    long countActiveByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count available items by category
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.available = true")
    long countAvailableByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count all available items
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.active = true AND i.available = true")
    long countAvailable();

    /**
     * Count all unavailable items (active but not available)
     */
    @Query("SELECT COUNT(i) FROM ItemMenu i WHERE i.active = true AND i.available = false")
    long countUnavailable();

    /**
     * Find items with low stock (unavailable due to ingredient shortage)
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.active = true AND i.available = false ORDER BY i.name ASC")
    List<ItemMenu> findItemsWithLowStock();

    /**
     * Search items by name (case insensitive)
     */
    @Query("SELECT i FROM ItemMenu i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY i.name ASC")
    List<ItemMenu> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Find items by category and availability
     */
    @Query("SELECT i FROM ItemMenu i WHERE i.category.idCategory = :categoryId AND i.active = true AND i.available = :available ORDER BY i.name ASC")
    List<ItemMenu> findByCategoryIdAndAvailability(@Param("categoryId") Long categoryId, 
                                                     @Param("available") Boolean available);
}
