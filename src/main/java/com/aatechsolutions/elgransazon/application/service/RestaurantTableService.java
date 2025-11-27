package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for RestaurantTable management
 */
public interface RestaurantTableService {

    /**
     * Find all tables
     */
    List<RestaurantTable> findAll();

    /**
     * Find all tables ordered by table number
     */
    List<RestaurantTable> findAllOrderByTableNumber();

    /**
     * Find table by ID
     */
    Optional<RestaurantTable> findById(Long id);

    /**
     * Find table by table number
     */
    Optional<RestaurantTable> findByTableNumber(Integer tableNumber);

    /**
     * Create a new table
     */
    RestaurantTable create(RestaurantTable table, String username);

    /**
     * Update an existing table
     */
    RestaurantTable update(Long id, RestaurantTable table, String username);

    /**
     * Change table status
     */
    RestaurantTable changeStatus(Long id, TableStatus status, String username);

    /**
     * Find tables by status
     */
    List<RestaurantTable> findByStatus(TableStatus status);

    /**
     * Find available tables
     */
    List<RestaurantTable> findAvailableTables();

    /**
     * Check if a RESERVED table can be occupied now according to reservation rules
     * (does not mutate DB). Returns true only when table is RESERVED, not currently
     * isOccupied, not OUT_OF_SERVICE and the time validation passes.
     */
    boolean canBeOccupiedNow(Long tableId);

    /**
     * Find tables that can be reserved (excluding OUT_OF_SERVICE)
     */
    List<RestaurantTable> findReservableTables();

    /**
     * Find tables by location
     */
    List<RestaurantTable> findByLocation(String location);

    /**
     * Find tables with minimum capacity
     */
    List<RestaurantTable> findByMinimumCapacity(Integer capacity);

    /**
     * Count tables by status
     */
    long countByStatus(TableStatus status);

    /**
     * Count all occupied tables (status=OCCUPIED + status=RESERVED with isOccupied=true)
     */
    long countAllOccupiedTables();

    /**
     * Count reserved tables that are NOT occupied (isOccupied = false)
     */
    long countReservedOnly();

    /**
     * Count all tables
     */
    long countAll();

    /**
     * Check if table number exists
     */
    boolean existsByTableNumber(Integer tableNumber);

    /**
     * Check if table number exists excluding a specific id (for updates)
     */
    boolean existsByTableNumberAndIdNot(Integer tableNumber, Long excludeId);

    /**
     * Get all distinct locations
     */
    List<String> getDistinctLocations();

    /**
     * Find table by ID or throw exception
     */
    RestaurantTable findByIdOrThrow(Long id);

    /**
     * Save a table (for internal use)
     */
    RestaurantTable save(RestaurantTable table);

    /**
     * Mark a reserved table as occupied (if validation passes)
     */
    RestaurantTable markAsOccupied(Long id, String username);
}
