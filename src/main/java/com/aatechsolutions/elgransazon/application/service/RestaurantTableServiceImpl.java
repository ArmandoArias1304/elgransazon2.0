package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Reservation;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.entity.TableStatus;
import com.aatechsolutions.elgransazon.domain.repository.ReservationRepository;
import com.aatechsolutions.elgransazon.domain.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of RestaurantTableService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RestaurantTableServiceImpl implements RestaurantTableService {

    private final RestaurantTableRepository tableRepository;
    private final ReservationRepository reservationRepository;
    private final SystemConfigurationService systemConfigurationService;

    @Override
    public List<RestaurantTable> findAll() {
        log.debug("Fetching all restaurant tables");
        return tableRepository.findAll();
    }

    @Override
    public List<RestaurantTable> findAllOrderByTableNumber() {
        log.debug("Fetching all restaurant tables ordered by table number");
        return tableRepository.findAllOrderByTableNumber();
    }

    @Override
    public Optional<RestaurantTable> findById(Long id) {
        log.debug("Finding restaurant table by ID: {}", id);
        return tableRepository.findById(id);
    }

    @Override
    public Optional<RestaurantTable> findByTableNumber(Integer tableNumber) {
        log.debug("Finding restaurant table by table number: {}", tableNumber);
        return tableRepository.findByTableNumber(tableNumber);
    }

    @Override
    @Transactional
    public RestaurantTable create(RestaurantTable table, String username) {
        log.info("Creating new restaurant table: {}", table.getTableNumber());

        // Validate table number is unique
        if (tableRepository.existsByTableNumber(table.getTableNumber())) {
            String error = "El número de mesa " + table.getTableNumber() + " ya existe";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Validate capacity
        if (table.getCapacity() == null || table.getCapacity() < 1) {
            String error = "La capacidad debe ser al menos 1 persona";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Set audit fields
        table.setCreatedBy(username);
        table.setUpdatedBy(username);
        table.setCreatedAt(LocalDateTime.now());
        table.setUpdatedAt(LocalDateTime.now());

        // Set default status if not provided
        if (table.getStatus() == null) {
            table.setStatus(TableStatus.AVAILABLE);
        }

        RestaurantTable saved = tableRepository.save(table);
        log.info("Restaurant table created successfully with ID: {}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public RestaurantTable update(Long id, RestaurantTable table, String username) {
        log.info("Updating restaurant table with ID: {}", id);

        RestaurantTable existing = tableRepository.findById(id)
                .orElseThrow(() -> {
                    String error = "Mesa no encontrada con ID: " + id;
                    log.error(error);
                    return new IllegalArgumentException(error);
                });

        // Validate table number is unique (excluding current table)
        if (!existing.getTableNumber().equals(table.getTableNumber()) &&
            tableRepository.existsByTableNumber(table.getTableNumber())) {
            String error = "El número de mesa " + table.getTableNumber() + " ya existe";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Validate capacity
        if (table.getCapacity() == null || table.getCapacity() < 1) {
            String error = "La capacidad debe ser al menos 1 persona";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Update fields
        existing.setTableNumber(table.getTableNumber());
        existing.setCapacity(table.getCapacity());
        existing.setLocation(table.getLocation());
        existing.setStatus(table.getStatus());
        existing.setComments(table.getComments());
        existing.setUpdatedBy(username);
        existing.setUpdatedAt(LocalDateTime.now());

        RestaurantTable updated = tableRepository.save(existing);
        log.info("Restaurant table updated successfully: {}", updated.getId());
        return updated;
    }

    @Override
    @Transactional
    public RestaurantTable changeStatus(Long id, TableStatus status, String username) {
        log.info("Changing status of table ID {} to {}", id, status);

        RestaurantTable table = tableRepository.findById(id)
                .orElseThrow(() -> {
                    String error = "Mesa no encontrada con ID: " + id;
                    log.error(error);
                    return new IllegalArgumentException(error);
                });

        table.setStatus(status);
        table.setUpdatedBy(username);
        table.setUpdatedAt(LocalDateTime.now());

        RestaurantTable updated = tableRepository.save(table);
        log.info("Table status changed successfully: {} -> {}", id, status);
        return updated;
    }

    @Override
    public List<RestaurantTable> findByStatus(TableStatus status) {
        log.debug("Finding tables by status: {}", status);
        return tableRepository.findByStatus(status);
    }

    @Override
    public List<RestaurantTable> findAvailableTables() {
        log.debug("Finding available tables");
        return tableRepository.findAvailableTablesOrderByCapacity();
    }

    @Override
    public List<RestaurantTable> findReservableTables() {
        log.debug("Finding tables that can be reserved (excluding OUT_OF_SERVICE)");
        return tableRepository.findAll().stream()
                .filter(table -> table.getStatus() != TableStatus.OUT_OF_SERVICE)
                .sorted((t1, t2) -> t1.getTableNumber().compareTo(t2.getTableNumber()))
                .toList();
    }

    @Override
    public List<RestaurantTable> findByLocation(String location) {
        log.debug("Finding tables by location: {}", location);
        return tableRepository.findByLocation(location);
    }

    @Override
    public List<RestaurantTable> findByMinimumCapacity(Integer capacity) {
        log.debug("Finding tables with minimum capacity: {}", capacity);
        return tableRepository.findByCapacityGreaterThanEqual(capacity);
    }

    @Override
    public long countByStatus(TableStatus status) {
        log.debug("Counting tables by status: {}", status);
        return tableRepository.countByStatus(status);
    }

    @Override
    public long countAllOccupiedTables() {
        log.debug("Counting all occupied tables (OCCUPIED status + RESERVED with isOccupied=true)");
        long occupiedStatus = tableRepository.countByStatus(TableStatus.OCCUPIED);
        long reservedOccupied = tableRepository.countByStatusAndIsOccupied(TableStatus.RESERVED, true);
        return occupiedStatus + reservedOccupied;
    }

    @Override
    public long countReservedOnly() {
        log.debug("Counting reserved tables that are NOT occupied");
        return tableRepository.countByStatusAndIsOccupied(TableStatus.RESERVED, false);
    }

    @Override
    public long countAll() {
        log.debug("Counting all tables");
        return tableRepository.count();
    }

    @Override
    public boolean existsByTableNumber(Integer tableNumber) {
        return tableRepository.existsByTableNumber(tableNumber);
    }

    @Override
    public boolean existsByTableNumberAndIdNot(Integer tableNumber, Long excludeId) {
        return tableRepository.existsByTableNumberAndIdNot(tableNumber, excludeId);
    }

    @Override
    public List<String> getDistinctLocations() {
        log.debug("Fetching distinct locations");
        return tableRepository.findDistinctLocations();
    }

    @Override
    public RestaurantTable findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> {
                    String error = "Mesa no encontrada con ID: " + id;
                    log.error(error);
                    return new IllegalArgumentException(error);
                });
    }

    @Override
    @Transactional
    public RestaurantTable save(RestaurantTable table) {
        log.debug("Saving restaurant table: {}", table.getId());
        return tableRepository.save(table);
    }

    @Override
    @Transactional
    public RestaurantTable markAsOccupied(Long id, String username) {
        log.info("Attempting to mark table {} as occupied by user: {}", id, username);

        RestaurantTable table = findByIdOrThrow(id);

        // Table must be in RESERVED status
        if (table.getStatus() != TableStatus.RESERVED) {
            String error = "Solo se puede ocupar una mesa que esté reservada. Estado actual: " + 
                    table.getStatusDisplayName();
            log.error(error);
            throw new IllegalStateException(error);
        }

        // Get system configuration for average consumption time
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        Integer avgConsumptionMinutes = config.getAverageConsumptionTimeMinutes();
        
        // Get current date and time
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        
        // Find next reservation for this table (today or future)
        Optional<Reservation> nextReservationOpt = reservationRepository.findNextReservationForTable(
                table.getId(), 
                today, 
                now
        );
        
        // If there's a next reservation, validate there's enough time
        if (nextReservationOpt.isPresent()) {
            Reservation nextReservation = nextReservationOpt.get();
            LocalDate nextReservationDate = nextReservation.getReservationDate();
            LocalTime nextReservationTime = nextReservation.getReservationTime();
            LocalTime estimatedEndTime = now.plusMinutes(avgConsumptionMinutes);
            
            // Only validate time if reservation is TODAY
            // If reservation is tomorrow or later, we can always occupy (no time conflict)
            if (nextReservationDate.equals(today)) {
                // Check if estimated end time is after next reservation time
                if (estimatedEndTime.isAfter(nextReservationTime)) {
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    String error = String.format(
                        "No hay tiempo suficiente antes de la próxima reservación. " +
                        "Próxima reservación: %s. Tiempo estimado de consumo: %d minutos. " +
                        "Hora actual: %s. Hora estimada de finalización: %s.",
                        nextReservationTime.format(timeFormatter),
                        avgConsumptionMinutes,
                        now.format(timeFormatter),
                        estimatedEndTime.format(timeFormatter)
                    );
                    log.error(error);
                    throw new IllegalStateException(error);
                }
                
                log.info("Next reservation TODAY at {} - Estimated end time: {} - Validation passed", 
                        nextReservationTime, estimatedEndTime);
            } else {
                log.info("Next reservation is on {} (not today) - Can occupy without time restriction", 
                        nextReservationDate);
            }
        }

        // Mark as occupied
        table.setIsOccupied(true);
        table.setUpdatedBy(username);
        table.setUpdatedAt(LocalDateTime.now());

        RestaurantTable updated = tableRepository.save(table);
        log.info("Table {} marked as occupied successfully", id);
        return updated;
    }

    @Override
    public boolean canBeOccupiedNow(Long id) {
        log.debug("Checking if reserved table {} can be occupied now", id);

        RestaurantTable table = findByIdOrThrow(id);

        // Only evaluate RESERVED tables that are not currently occupied
        if (table.getStatus() != TableStatus.RESERVED) {
            log.debug("Table {} is not RESERVED (status={}), cannot use reserved-check", id, table.getStatus());
            return false;
        }

        if (Boolean.TRUE.equals(table.getIsOccupied())) {
            log.debug("Table {} is already marked isOccupied=true", id);
            return false;
        }

        if (table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            log.debug("Table {} is OUT_OF_SERVICE", id);
            return false;
        }

        // Get system configuration for average consumption time
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        Integer avgConsumptionMinutes = config.getAverageConsumptionTimeMinutes();

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        Optional<Reservation> nextReservationOpt = reservationRepository.findNextReservationForTable(
                table.getId(),
                today,
                now
        );

        if (nextReservationOpt.isPresent()) {
            Reservation nextReservation = nextReservationOpt.get();
            LocalDate nextReservationDate = nextReservation.getReservationDate();
            LocalTime nextReservationTime = nextReservation.getReservationTime();
            
            // Calculate estimated end time for current occupation
            LocalTime estimatedEndTime = now.plusMinutes(avgConsumptionMinutes);
            
            // If reservation is today, check if there's enough time
            if (nextReservationDate.equals(today)) {
                if (estimatedEndTime.isAfter(nextReservationTime)) {
                    log.debug("Table {} cannot be occupied because estimated end time {} is after next reservation {} (same day)",
                            id, estimatedEndTime, nextReservationTime);
                    return false;
                }
            }
            // If reservation is tomorrow or later, we can occupy (today's use won't conflict)
            // No need to check - there's enough time
        }

        log.debug("Table {} can be occupied now", id);
        return true;
    }
}
