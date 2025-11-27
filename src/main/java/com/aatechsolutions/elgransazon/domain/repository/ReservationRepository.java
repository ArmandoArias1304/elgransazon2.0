package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Reservation;
import com.aatechsolutions.elgransazon.domain.entity.ReservationStatus;
import com.aatechsolutions.elgransazon.domain.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Reservation entity
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Find all reservations ordered by date and time descending
     */
    List<Reservation> findAllByOrderByReservationDateDescReservationTimeDesc();

    /**
     * Find reservations by table and status
     */
    List<Reservation> findByRestaurantTableAndStatus(RestaurantTable table, ReservationStatus status);

    /**
     * Find reservations by table and status (multiple statuses)
     */
    List<Reservation> findByRestaurantTableAndStatusIn(RestaurantTable table, List<ReservationStatus> statuses);

    /**
     * Find reservations by table and date ordered by time
     */
    List<Reservation> findByRestaurantTableAndReservationDateOrderByReservationTimeAsc(
            RestaurantTable table, LocalDate date);

    /**
     * Find reservations by date range
     */
    List<Reservation> findByReservationDateBetweenOrderByReservationDateAscReservationTimeAsc(
            LocalDate startDate, LocalDate endDate);

    /**
     * Find reservations by date
     */
    List<Reservation> findByReservationDateOrderByReservationTimeAsc(LocalDate date);

    /**
     * Find reservations by status
     */
    List<Reservation> findByStatusOrderByReservationDateDescReservationTimeDesc(ReservationStatus status);

    /**
     * Find active reservations (RESERVED, OCCUPIED)
     */
    @Query("SELECT r FROM Reservation r WHERE r.status IN ('RESERVED', 'OCCUPIED') " +
           "ORDER BY r.reservationDate DESC, r.reservationTime DESC")
    List<Reservation> findActiveReservations();

    /**
     * Find upcoming reservations (today or future, active statuses)
     */
    @Query("SELECT r FROM Reservation r WHERE r.reservationDate >= :today " +
           "AND r.status IN ('RESERVED', 'OCCUPIED') " +
           "ORDER BY r.reservationDate ASC, r.reservationTime ASC")
    List<Reservation> findUpcomingReservations(@Param("today") LocalDate today);

    /**
     * Find reservations for today
     */
    @Query("SELECT r FROM Reservation r WHERE r.reservationDate = :today " +
           "ORDER BY r.reservationTime ASC")
    List<Reservation> findTodayReservations(@Param("today") LocalDate today);

    /**
     * Find next reservation for a specific table (returns the closest one)
     */
    @Query(value = "SELECT * FROM reservations r WHERE r.id_table = :tableId " +
           "AND r.status = 'RESERVED' " +
           "AND (r.reservation_date > :currentDate " +
           "OR (r.reservation_date = :currentDate AND r.reservation_time > :currentTime)) " +
           "ORDER BY r.reservation_date ASC, r.reservation_time ASC " +
           "LIMIT 1", nativeQuery = true)
    Optional<Reservation> findNextReservationForTable(
            @Param("tableId") Long tableId,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime);

    /**
     * Check if there's an overlapping reservation for a table
     * (excluding a specific reservation ID for updates)
     */
    @Query(value = "SELECT COUNT(r.id_reservation) FROM reservations r " +
           "WHERE r.id_table = :tableId " +
           "AND r.reservation_date = :date " +
           "AND r.status IN ('RESERVED', 'OCCUPIED') " +
           "AND (:reservationId IS NULL OR r.id_reservation != :reservationId) " +
           "AND r.reservation_time < :endTime " +
           "AND ADDTIME(r.reservation_time, SEC_TO_TIME(:avgConsumptionSeconds)) > :startTime",
           nativeQuery = true)
    Long countOverlappingReservations(
            @Param("tableId") Long tableId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("avgConsumptionSeconds") Integer avgConsumptionSeconds,
            @Param("reservationId") Long reservationId);

    /**
     * Count reservations by status
     */
    long countByStatus(ReservationStatus status);

    /**
     * Count reservations for today
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.reservationDate = :today")
    long countTodayReservations(@Param("today") LocalDate today);

    /**
     * Count active reservations for today
     */
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.reservationDate = :today " +
           "AND r.status IN ('RESERVED', 'OCCUPIED')")
    long countTodayActiveReservations(@Param("today") LocalDate today);

    /**
     * Find reservations by customer phone
     */
    List<Reservation> findByCustomerPhoneContainingOrderByReservationDateDescReservationTimeDesc(String phone);

    /**
     * Find reservations by customer name
     */
    List<Reservation> findByCustomerNameContainingIgnoreCaseOrderByReservationDateDescReservationTimeDesc(String name);
}
