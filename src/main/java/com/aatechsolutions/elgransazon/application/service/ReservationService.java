package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing restaurant reservations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RestaurantTableService tableService;
    private final SystemConfigurationService systemConfigurationService;

    /**
     * Find all reservations ordered by date and time (descending)
     */
    public List<Reservation> findAllOrderByDateTimeDesc() {
        log.debug("Finding all reservations ordered by date and time desc");
        return reservationRepository.findAllByOrderByReservationDateDescReservationTimeDesc();
    }

    /**
     * Find reservation by ID
     */
    public Optional<Reservation> findById(Long id) {
        log.debug("Finding reservation by id: {}", id);
        return reservationRepository.findById(id);
    }

    /**
     * Find reservation by ID or throw exception
     */
    public Reservation findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservación no encontrada con ID: " + id));
    }

    /**
     * Find active reservations
     */
    public List<Reservation> findActiveReservations() {
        log.debug("Finding active reservations");
        return reservationRepository.findActiveReservations();
    }

    /**
     * Find upcoming reservations (today or future)
     */
    public List<Reservation> findUpcomingReservations() {
        log.debug("Finding upcoming reservations");
        return reservationRepository.findUpcomingReservations(LocalDate.now());
    }

    /**
     * Find reservations for today
     */
    public List<Reservation> findTodayReservations() {
        log.debug("Finding today's reservations");
        return reservationRepository.findTodayReservations(LocalDate.now());
    }

    /**
     * Find reservations by date
     */
    public List<Reservation> findByDate(LocalDate date) {
        log.debug("Finding reservations for date: {}", date);
        return reservationRepository.findByReservationDateOrderByReservationTimeAsc(date);
    }

    /**
     * Find reservations by date range
     */
    public List<Reservation> findByDateRange(LocalDate startDate, LocalDate endDate) {
        log.debug("Finding reservations between {} and {}", startDate, endDate);
        return reservationRepository.findByReservationDateBetweenOrderByReservationDateAscReservationTimeAsc(
                startDate, endDate);
    }

    /**
     * Find reservations by status
     */
    public List<Reservation> findByStatus(ReservationStatus status) {
        log.debug("Finding reservations with status: {}", status);
        return reservationRepository.findByStatusOrderByReservationDateDescReservationTimeDesc(status);
    }

    /**
     * Find next reservation for a table
     */
    public Optional<Reservation> findNextReservationForTable(Long tableId) {
        log.debug("Finding next reservation for table: {}", tableId);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        return reservationRepository.findNextReservationForTable(tableId, today, now);
    }

    /**
     * Find reservations by customer phone
     */
    public List<Reservation> findByCustomerPhone(String phone) {
        log.debug("Finding reservations for customer phone: {}", phone);
        return reservationRepository.findByCustomerPhoneContainingOrderByReservationDateDescReservationTimeDesc(phone);
    }

    /**
     * Find reservations by customer name
     */
    public List<Reservation> findByCustomerName(String name) {
        log.debug("Finding reservations for customer name: {}", name);
        return reservationRepository.findByCustomerNameContainingIgnoreCaseOrderByReservationDateDescReservationTimeDesc(name);
    }

    /**
     * Count reservations by status
     */
    public long countByStatus(ReservationStatus status) {
        return reservationRepository.countByStatus(status);
    }

    /**
     * Count today's reservations
     */
    public long countTodayReservations() {
        return reservationRepository.countTodayReservations(LocalDate.now());
    }

    /**
     * Count today's active reservations
     */
    public long countTodayActiveReservations() {
        return reservationRepository.countTodayActiveReservations(LocalDate.now());
    }

    /**
     * Create a new reservation with validations
     */
    @Transactional
    public Reservation create(Reservation reservation, String username) {
        log.info("Creating new reservation for customer: {} by user: {}", 
                reservation.getCustomerName(), username);

        // Load full table entity (Spring binding only sets the ID)
        RestaurantTable table = tableService.findByIdOrThrow(reservation.getRestaurantTable().getId());
        reservation.setRestaurantTable(table);

        // Set audit fields
        reservation.setCreatedBy(username);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setIsOccupied(false);

        // Validate reservation
        validateReservation(reservation, null);

        // Save reservation
        Reservation saved = reservationRepository.save(reservation);

        // Update table status to RESERVED
        updateTableStatus(saved.getRestaurantTable().getId());

        log.info("Reservation created successfully with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Update an existing reservation
     */
    @Transactional
    public Reservation update(Long id, Reservation updatedReservation, String username) {
        log.info("Updating reservation: {} by user: {}", id, username);

        Reservation existing = findByIdOrThrow(id);

        // Check if reservation can be edited
        if (!existing.isEditable()) {
            throw new IllegalStateException("No se puede editar una reservación en estado: " + 
                    existing.getStatusDisplayName());
        }

        // Store old table ID
        Long oldTableId = existing.getRestaurantTable().getId();
        Long newTableId = updatedReservation.getRestaurantTable().getId();

        // Load full table entity if table is being changed
        RestaurantTable newTable = tableService.findByIdOrThrow(newTableId);

        // Update fields
        existing.setCustomerName(updatedReservation.getCustomerName());
        existing.setCustomerPhone(updatedReservation.getCustomerPhone());
        existing.setCustomerEmail(updatedReservation.getCustomerEmail());
        existing.setNumberOfGuests(updatedReservation.getNumberOfGuests());
        existing.setReservationDate(updatedReservation.getReservationDate());
        existing.setReservationTime(updatedReservation.getReservationTime());
        existing.setSpecialRequests(updatedReservation.getSpecialRequests());
        existing.setRestaurantTable(newTable);
        existing.setUpdatedBy(username);

        // Validate reservation
        validateReservation(existing, id);

        // Save reservation
        Reservation saved = reservationRepository.save(existing);

        // Update table statuses if table changed
        if (!oldTableId.equals(newTableId)) {
            updateTableStatus(oldTableId);
            updateTableStatus(newTableId);
        }

        log.info("Reservation updated successfully: {}", id);
        return saved;
    }

    /**
     * Change reservation status
     */
    @Transactional
    public Reservation changeStatus(Long id, ReservationStatus newStatus, String username) {
        log.info("Changing reservation {} status to {} by user: {}", id, newStatus, username);

        Reservation reservation = findByIdOrThrow(id);
        ReservationStatus oldStatus = reservation.getStatus();

        // Validate status transition
        validateStatusTransition(reservation, newStatus);

        // Update status
        reservation.setStatus(newStatus);
        reservation.setUpdatedBy(username);

        // Handle status-specific logic
        switch (newStatus) {
            case OCCUPIED:
                // Mark reservation as occupied
                reservation.setIsOccupied(true);
                break;
            case COMPLETED:
            case CANCELLED:
            case NO_SHOW:
                // Mark as not occupied
                reservation.setIsOccupied(false);
                break;
            case RESERVED:
                // No specific action needed
                break;
        }

        // Save reservation
        Reservation saved = reservationRepository.save(reservation);

        // Update table status
        updateTableStatus(saved.getRestaurantTable().getId());

        log.info("Reservation status changed from {} to {}", oldStatus, newStatus);
        return saved;
    }

    /**
     * Cancel a reservation
     */
    @Transactional
    public Reservation cancel(Long id, String username) {
        return changeStatus(id, ReservationStatus.CANCELLED, username);
    }

    /**
     * Check-in a reservation (mark as occupied)
     */
    @Transactional
    public Reservation checkIn(Long id, String username) {
        return changeStatus(id, ReservationStatus.OCCUPIED, username);
    }

    /**
     * Check-out a reservation (mark as completed)
     */
    @Transactional
    public Reservation checkOut(Long id, String username) {
        return changeStatus(id, ReservationStatus.COMPLETED, username);
    }

    /**
     * Mark reservation as no-show
     */
    @Transactional
    public Reservation markAsNoShow(Long id, String username) {
        return changeStatus(id, ReservationStatus.NO_SHOW, username);
    }

    /**
     * Delete a reservation (soft delete by cancelling)
     */
    @Transactional
    public void delete(Long id, String username) {
        log.info("Deleting reservation: {} by user: {}", id, username);
        cancel(id, username);
    }

    /**
     * Validate reservation business rules
     */
    private void validateReservation(Reservation reservation, Long excludeId) {
        // 1. Validate table is not out of service
        validateTableAvailability(reservation.getRestaurantTable());

        // 2. Validate date is today or future
        validateReservationDate(reservation.getReservationDate());

        // 3. Validate time is within business hours
        validateReservationTime(reservation.getReservationDate(), reservation.getReservationTime());

        // 4. Validate table capacity
        validateTableCapacity(reservation.getRestaurantTable(), reservation.getNumberOfGuests());

        // 5. Validate no overlapping reservations
        validateNoOverlappingReservations(
                reservation.getRestaurantTable().getId(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                excludeId
        );
    }

    /**
     * Validate table is available for reservation (not OUT_OF_SERVICE)
     */
    private void validateTableAvailability(RestaurantTable table) {
        if (table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw new IllegalArgumentException(
                    String.format("La mesa %s está fuera de servicio y no se puede reservar",
                            table.getDisplayName()));
        }
    }

    /**
     * Validate reservation date (must be today or future)
     */
    private void validateReservationDate(LocalDate reservationDate) {
        LocalDate today = LocalDate.now();
        if (reservationDate.isBefore(today)) {
            throw new IllegalArgumentException("La fecha de reservación debe ser hoy o una fecha futura");
        }
    }

    /**
     * Validate reservation time is within business hours
     * Time must be within open time and (close time - average consumption time)
     */
    private void validateReservationTime(LocalDate reservationDate, LocalTime reservationTime) {
        SystemConfiguration config = systemConfigurationService.getConfiguration();

        // 0. If reservation is for today, check that time is not in the past
        LocalDate today = LocalDate.now();
        if (reservationDate.equals(today)) {
            LocalTime currentTime = LocalTime.now();
            if (reservationTime.isBefore(currentTime) || reservationTime.equals(currentTime)) {
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                throw new IllegalArgumentException(
                        String.format("No se puede crear una reservación en el pasado. La hora actual es %s y la hora seleccionada es %s",
                                currentTime.format(timeFormatter),
                                reservationTime.format(timeFormatter)));
            }
        }

        // Convert LocalDate to DayOfWeek
        java.time.DayOfWeek javaDayOfWeek = reservationDate.getDayOfWeek();
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(javaDayOfWeek.name());

        // 1. Check if day is a work day
        if (!config.isWorkDay(dayOfWeek)) {
            throw new IllegalArgumentException("El día seleccionado (" + dayOfWeek.getDisplayName() + 
                    ") no es un día laborable");
        }

        // 2. Get business hours for that day
        BusinessHours businessHours = config.getBusinessHoursForDay(dayOfWeek)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay horario configurado para " + dayOfWeek.getDisplayName()));

        // 3. Check if restaurant is closed that day
        if (businessHours.getIsClosed()) {
            throw new IllegalArgumentException("El restaurante está cerrado los " + dayOfWeek.getDisplayName());
        }

        LocalTime openTime = businessHours.getOpenTime();
        LocalTime closeTime = businessHours.getCloseTime();
        Integer avgConsumption = config.getAverageConsumptionTimeMinutes();

        // 4. Calculate last reservation time (closeTime - avgConsumption)
        LocalTime lastReservationTime = closeTime.minusMinutes(avgConsumption);

        // 5. Validate reservation time is within valid range
        if (reservationTime.isBefore(openTime)) {
            throw new IllegalArgumentException(
                    String.format("La hora de reservación debe ser después de las %s (hora de apertura)",
                            openTime.toString()));
        }

        if (reservationTime.isAfter(lastReservationTime)) {
            throw new IllegalArgumentException(
                    String.format("La última reservación permitida es a las %s (considerando %s de tiempo de consumo antes del cierre a las %s)",
                            lastReservationTime.toString(),
                            config.getAverageConsumptionTimeDisplay(),
                            closeTime.toString()));
        }
    }

    /**
     * Validate table capacity
     */
    private void validateTableCapacity(RestaurantTable table, Integer numberOfGuests) {
        if (table.getCapacity() == null) {
            throw new IllegalStateException(
                    String.format("La mesa %s no tiene capacidad definida", table.getDisplayName()));
        }
        
        if (numberOfGuests > table.getCapacity()) {
            throw new IllegalArgumentException(
                    String.format("La mesa %s tiene capacidad para %d personas, no para %d",
                            table.getDisplayName(),
                            table.getCapacity(),
                            numberOfGuests));
        }
    }

    /**
     * Validate no overlapping reservations
     */
    private void validateNoOverlappingReservations(Long tableId, LocalDate date, 
                                                   LocalTime startTime, Long excludeId) {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        Integer avgConsumption = config.getAverageConsumptionTimeMinutes();

        log.debug("=== Validating overlapping reservations ===");
        log.debug("Table ID: {}", tableId);
        log.debug("Date: {}", date);
        log.debug("Start time: {}", startTime);
        log.debug("Avg consumption: {} minutes", avgConsumption);

        // Calculate end time
        LocalTime endTime = startTime.plusMinutes(avgConsumption);
        log.debug("Calculated end time: {}", endTime);

        // Convert average consumption to seconds for the native query
        Integer avgConsumptionSeconds = avgConsumption * 60;
        log.debug("Avg consumption seconds: {}", avgConsumptionSeconds);

        Long overlapCount = reservationRepository.countOverlappingReservations(
                tableId, date, startTime, endTime, avgConsumptionSeconds, excludeId);

        log.debug("Overlap count: {}", overlapCount);

        if (overlapCount > 0) {
            log.warn("Overlap detected! Count: {}, Config time: {} min ({})", 
                overlapCount, avgConsumption, config.getAverageConsumptionTimeDisplay());
            throw new IllegalArgumentException(
                    "Ya existe una reservación para esta mesa en el horario solicitado. " +
                    "Debe haber al menos " + config.getAverageConsumptionTimeDisplay() + 
                    " entre reservaciones.");
        }
        
        log.debug("=== Validation passed - No overlaps detected ===");
    }

    /**
     * Validate status transition
     */
    private void validateStatusTransition(Reservation reservation, ReservationStatus newStatus) {
        ReservationStatus currentStatus = reservation.getStatus();

        // Cannot change from terminal states
        if (currentStatus == ReservationStatus.COMPLETED ||
            currentStatus == ReservationStatus.CANCELLED ||
            currentStatus == ReservationStatus.NO_SHOW) {
            throw new IllegalStateException(
                    "No se puede cambiar el estado de una reservación " + 
                    currentStatus.getDisplayName());
        }

        // Specific validations based on new status
        if (newStatus == ReservationStatus.OCCUPIED) {
            if (currentStatus != ReservationStatus.RESERVED) {
                throw new IllegalStateException(
                        "Solo se puede marcar como ocupada una reservación reservada");
            }
        }

        if (newStatus == ReservationStatus.COMPLETED) {
            if (currentStatus != ReservationStatus.OCCUPIED) {
                throw new IllegalStateException(
                        "Solo se puede completar una reservación que esté ocupada");
            }
        }
    }

    /**
     * Update table status based on its reservations
     */
    private void updateTableStatus(Long tableId) {
        RestaurantTable table = tableService.findByIdOrThrow(tableId);

        // Find active reservations for this table
        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.RESERVED,
                ReservationStatus.OCCUPIED
        );

        List<Reservation> activeReservations = reservationRepository
                .findByRestaurantTableAndStatusIn(table, activeStatuses);

        // Check if any reservation is currently occupied
        boolean hasOccupiedReservation = activeReservations.stream()
                .anyMatch(r -> r.getStatus() == ReservationStatus.OCCUPIED);

        if (hasOccupiedReservation) {
            // Table is occupied
            table.setStatus(TableStatus.OCCUPIED);
            table.setIsOccupied(false); // Not reserved-occupied, just occupied
        } else if (!activeReservations.isEmpty()) {
            // Table has reservations but not currently occupied
            table.setStatus(TableStatus.RESERVED);
            table.setIsOccupied(false);
        } else {
            // No active reservations
            table.setStatus(TableStatus.AVAILABLE);
            table.setIsOccupied(false);
        }

        tableService.save(table);
    }
}
