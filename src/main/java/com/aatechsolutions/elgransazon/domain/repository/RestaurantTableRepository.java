package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RestaurantTable entity
 */
@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    /**
     * Find table by table number
     */
    Optional<RestaurantTable> findByTableNumber(Integer tableNumber);

    /**
     * Find all tables by status
     */
    List<RestaurantTable> findByStatus(TableStatus status);

    /**
     * Find all tables by location
     */
    List<RestaurantTable> findByLocation(String location);

    /**
     * Find tables with capacity greater than or equal to specified value
     */
    List<RestaurantTable> findByCapacityGreaterThanEqual(Integer capacity);

    /**
     * Count tables by status
     */
    long countByStatus(TableStatus status);

    /**
     * Count tables by status and occupied flag
     */
    long countByStatusAndIsOccupied(TableStatus status, Boolean isOccupied);

    /**
     * Check if table number exists
     */
    boolean existsByTableNumber(Integer tableNumber);

    /**
     * Check if table number exists excluding a specific id (for updates)
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM RestaurantTable t " +
           "WHERE t.tableNumber = :tableNumber AND t.id <> :excludeId")
    boolean existsByTableNumberAndIdNot(@Param("tableNumber") Integer tableNumber, 
                                        @Param("excludeId") Long excludeId);

    /**
     * Find all tables ordered by table number
     */
    @Query("SELECT t FROM RestaurantTable t ORDER BY t.tableNumber ASC")
    List<RestaurantTable> findAllOrderByTableNumber();

    /**
     * Find available tables ordered by capacity
     */
    @Query("SELECT t FROM RestaurantTable t WHERE t.status = 'AVAILABLE' ORDER BY t.capacity ASC")
    List<RestaurantTable> findAvailableTablesOrderByCapacity();

    /**
     * Get all distinct locations
     */
    @Query("SELECT DISTINCT t.location FROM RestaurantTable t WHERE t.location IS NOT NULL ORDER BY t.location")
    List<String> findDistinctLocations();
}
