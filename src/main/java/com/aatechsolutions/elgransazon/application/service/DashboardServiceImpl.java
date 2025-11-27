package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.*;
import com.aatechsolutions.elgransazon.presentation.dto.DashboardStatsDTO;
import com.aatechsolutions.elgransazon.presentation.dto.DashboardStatsDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of Dashboard service
 * Provides aggregated statistics for the admin dashboard
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    private final IngredientRepository ingredientRepository;
    private final RestaurantTableRepository tableRepository;
    private final ReservationRepository reservationRepository;

    @Override
    public DashboardStatsDTO getDashboardStats() {
        log.debug("Calculating dashboard statistics");

        // Get today's date range
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        
        // Get yesterday's date range
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);

        // Get orders for today and yesterday
        List<Order> todayOrders = orderRepository.findByDateRange(todayStart, todayEnd);
        List<Order> yesterdayOrders = orderRepository.findByDateRange(yesterdayStart, yesterdayEnd);

        // Calculate sales statistics
        BigDecimal todaySales = calculateSales(todayOrders);
        BigDecimal yesterdaySales = calculateSales(yesterdayOrders);
        Double salesChangePercentage = calculatePercentageChange(todaySales, yesterdaySales);

        // Calculate orders statistics
        Long todayOrdersCount = (long) todayOrders.size();
        Long yesterdayOrdersCount = (long) yesterdayOrders.size();
        Double ordersChangePercentage = calculatePercentageChange(
            BigDecimal.valueOf(todayOrdersCount), 
            BigDecimal.valueOf(yesterdayOrdersCount)
        );

        // Calculate customers statistics
        Long todayCustomers = countUniqueCustomers(todayOrders);
        Long yesterdayCustomers = countUniqueCustomers(yesterdayOrders);
        Double customersChangePercentage = calculatePercentageChange(
            BigDecimal.valueOf(todayCustomers), 
            BigDecimal.valueOf(yesterdayCustomers)
        );

        // Calculate projected revenue
        BigDecimal totalHistoricalRevenue = calculateTotalHistoricalRevenue();

        // Get popular items
        List<PopularItemDTO> popularItems = getPopularItems(todayOrders);

        // Get active employees
        Long totalEmployees = employeeRepository.count();
        Long activeEmployees = employeeRepository.countByEnabledTrue();
        Double capacityPercentage = totalEmployees > 0 
            ? (activeEmployees.doubleValue() / totalEmployees.doubleValue()) * 100 
            : 0.0;
        
        List<String> employeeInitials = employeeRepository.findByEnabledTrue()
            .stream()
            .limit(4)
            .map(emp -> {
                String firstName = emp.getNombre() != null && !emp.getNombre().isEmpty() 
                    ? emp.getNombre().substring(0, 1).toUpperCase() 
                    : "";
                String lastName = emp.getApellido() != null && !emp.getApellido().isEmpty() 
                    ? emp.getApellido().substring(0, 1).toUpperCase() 
                    : "";
                return firstName + lastName;
            })
            .collect(Collectors.toList());

        // Get inventory alerts (out of stock, low stock)
        List<InventoryAlertDTO> inventoryAlerts = getInventoryAlerts();

        // Get hourly sales for today
        List<HourlySalesDTO> hourlySales = getHourlySales(todayOrders);

        // Get table status
        TableStatusDTO tableStatus = getTableStatus();

        // Get pending orders summary
        PendingOrdersDTO pendingOrders = getPendingOrders();

        // Get today's reservations
        List<ReservationDTO> todayReservations = getTodayReservations();

        return DashboardStatsDTO.builder()
            .todaySales(todaySales)
            .salesChangePercentage(Math.abs(salesChangePercentage))
            .salesIncreased(salesChangePercentage >= 0)
            .todayOrders(todayOrdersCount)
            .ordersChangePercentage(Math.abs(ordersChangePercentage))
            .ordersIncreased(ordersChangePercentage >= 0)
            .todayCustomers(todayCustomers)
            .customersChangePercentage(Math.abs(customersChangePercentage))
            .customersIncreased(customersChangePercentage >= 0)
            .totalHistoricalRevenue(totalHistoricalRevenue)
            .popularItems(popularItems)
            .activeEmployees(activeEmployees.intValue())
            .totalEmployees(totalEmployees.intValue())
            .capacityPercentage(capacityPercentage)
            .employeeInitials(employeeInitials)
            .inventoryAlerts(inventoryAlerts)
            .hourlySales(hourlySales)
            .tableStatus(tableStatus)
            .pendingOrders(pendingOrders)
            .todayReservations(todayReservations)
            .build();
    }

    /**
     * Calculate total sales from orders (only PAID orders)
     * Only includes subtotal + tax, excludes tips
     */
    private BigDecimal calculateSales(List<Order> orders) {
        return orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .map(Order::getTotal) // Only subtotal + tax, no tips
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate percentage change between two values
     */
    private Double calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        
        BigDecimal change = current.subtract(previous);
        BigDecimal percentage = change
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        return percentage.doubleValue();
    }

    /**
     * Count unique customers from orders
     * Only counts orders with PAID status (completed visits)
     * Each PAID order = one customer/group that came, consumed, and left
     */
    private Long countUniqueCustomers(List<Order> orders) {
        return orders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .count();
    }

    /**
     * Calculate total historical revenue from all PAID orders (all time)
     */
    private BigDecimal calculateTotalHistoricalRevenue() {
        // Get ALL orders (no date filter)
        List<Order> allOrders = orderRepository.findAll();
        
        return allOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .map(Order::getTotal) // Subtotal + tax, no tips
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get top 10 popular items
     */
    private List<PopularItemDTO> getPopularItems(List<Order> todayOrders) {
        // Count items ordered today
        Map<String, Long> itemCounts = new HashMap<>();
        
        for (Order order : todayOrders) {
            if (order.getOrderDetails() != null) {
                for (OrderDetail detail : order.getOrderDetails()) {
                    if (detail.getItemMenu() != null) {
                        String itemName = detail.getItemMenu().getName();
                        Long quantity = detail.getQuantity().longValue();
                        itemCounts.merge(itemName, quantity, Long::sum);
                    }
                }
            }
        }
        
        // If no items today, try to get from all-time orders
        if (itemCounts.isEmpty()) {
            List<Order> allOrders = orderRepository.findAll();
            for (Order order : allOrders) {
                if (order.getOrderDetails() != null) {
                    for (OrderDetail detail : order.getOrderDetails()) {
                        if (detail.getItemMenu() != null) {
                            String itemName = detail.getItemMenu().getName();
                            Long quantity = detail.getQuantity().longValue();
                            itemCounts.merge(itemName, quantity, Long::sum);
                        }
                    }
                }
            }
        }
        
        // If still no items, return empty list
        if (itemCounts.isEmpty()) {
            return List.of();
        }
        
        // Sort by count and get top 10
        List<Map.Entry<String, Long>> sortedItems = itemCounts.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        // Get max count for percentage calculation
        Long maxCount = sortedItems.isEmpty() ? 1L : sortedItems.get(0).getValue();
        
        // Create DTOs
        List<PopularItemDTO> popularItems = new ArrayList<>();
        String[] colors = {"primary", "blue-500", "purple-500", "orange-500", "pink-500", "indigo-500", "teal-500", "amber-500", "rose-500", "cyan-500"};
        String[] gradients = {
            "from-primary to-primary-dark",
            "from-blue-400 to-blue-600",
            "from-purple-400 to-purple-600",
            "from-orange-400 to-orange-600",
            "from-pink-400 to-pink-600",
            "from-indigo-400 to-indigo-600",
            "from-teal-400 to-teal-600",
            "from-amber-400 to-amber-600",
            "from-rose-400 to-rose-600",
            "from-cyan-400 to-cyan-600"
        };
        
        for (int i = 0; i < sortedItems.size(); i++) {
            Map.Entry<String, Long> entry = sortedItems.get(i);
            Double percentage = (entry.getValue().doubleValue() / maxCount.doubleValue()) * 100;
            
            popularItems.add(PopularItemDTO.builder()
                .rank(i + 1)
                .itemName(entry.getKey())
                .orderCount(entry.getValue())
                .maxOrderCount(maxCount)
                .percentage(percentage)
                .color(colors[i % colors.length])
                .badgeGradient(gradients[i % gradients.length])
                .build());
        }
        
        return popularItems;
    }

    /**
     * Get inventory alerts (out of stock, low stock, healthy stock)
     * Returns top 3 items with most critical status
     */
    private List<InventoryAlertDTO> getInventoryAlerts() {
        // Get all active ingredients
        List<Ingredient> ingredients = ingredientRepository.findByActiveTrue();
        
        // If no ingredients, return empty list
        if (ingredients.isEmpty()) {
            return List.of();
        }
        
        // Separate by status
        List<Ingredient> outOfStock = new ArrayList<>();
        List<Ingredient> lowStock = new ArrayList<>();
        List<Ingredient> healthyStock = new ArrayList<>();
        
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isOutOfStock()) {
                outOfStock.add(ingredient);
            } else if (ingredient.isLowStock()) {
                lowStock.add(ingredient);
            } else if (ingredient.isHealthyStock()) {
                healthyStock.add(ingredient);
            }
        }
        
        // Build alert list (prioritize: out of stock > low stock > healthy)
        List<InventoryAlertDTO> alerts = new ArrayList<>();
        
        // Add out of stock (red)
        outOfStock.stream()
            .limit(5)
            .forEach(ingredient -> alerts.add(InventoryAlertDTO.builder()
                .ingredientName(ingredient.getName())
                .status("out-of-stock")
                .statusText("Agotado")
                .icon("error")
                .colorClass("red")
                .build()));
        
        // Add low stock (yellow) if we have less than 5 items
        if (alerts.size() < 5) {
            lowStock.stream()
                .limit(5 - alerts.size())
                .forEach(ingredient -> alerts.add(InventoryAlertDTO.builder()
                    .ingredientName(ingredient.getName())
                    .status("low-stock")
                    .statusText("Bajo stock")
                    .icon("warning")
                    .colorClass("yellow")
                    .build()));
        }
        
        // Add healthy stock (green) if we still have less than 5 items
        if (alerts.size() < 5) {
            healthyStock.stream()
                .limit(5 - alerts.size())
                .forEach(ingredient -> alerts.add(InventoryAlertDTO.builder()
                    .ingredientName(ingredient.getName())
                    .status("healthy")
                    .statusText("En stock")
                    .icon("check_circle")
                    .colorClass("green")
                    .build()));
        }
        
        return alerts;
    }

    private List<HourlySalesDTO> getHourlySales(List<Order> todayOrders) {
        Map<Integer, HourlySalesDTO> hourlySalesMap = new java.util.LinkedHashMap<>();
        
        // Initialize all hours (0-23) with zero values
        for (int hour = 0; hour < 24; hour++) {
            hourlySalesMap.put(hour, new HourlySalesDTO(hour, BigDecimal.ZERO, 0L));
        }
        
        // Process only PAID orders
        todayOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID)
            .forEach(order -> {
                LocalDateTime orderDate = order.getUpdatedAt() != null ? order.getUpdatedAt() : order.getCreatedAt();
                int hour = orderDate.getHour();
                
                HourlySalesDTO currentData = hourlySalesMap.get(hour);
                hourlySalesMap.put(hour, new HourlySalesDTO(
                    hour,
                    currentData.getSales().add(order.getTotal()),
                    currentData.getOrderCount() + 1
                ));
            });
        
        return new ArrayList<>(hourlySalesMap.values());
    }

    private TableStatusDTO getTableStatus() {
        List<RestaurantTable> allTables = tableRepository.findAll();
        
        int totalTables = allTables.size();
        long available = allTables.stream()
            .filter(table -> table.getStatus() == TableStatus.AVAILABLE)
            .count();
        long occupied = allTables.stream()
            .filter(table -> table.getStatus() == TableStatus.OCCUPIED)
            .count();
        long reserved = allTables.stream()
            .filter(table -> table.getStatus() == TableStatus.RESERVED)
            .count();
        long outOfService = allTables.stream()
            .filter(table -> table.getStatus() == TableStatus.OUT_OF_SERVICE)
            .count();
        
        return new TableStatusDTO(
            totalTables,
            (int) available,
            (int) occupied,
            (int) reserved,
            (int) outOfService
        );
    }

    private PendingOrdersDTO getPendingOrders() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        
        // Get all active orders for today
        List<Order> activeOrders = orderRepository.findByDateRange(today, now);
        
        long pending = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PENDING)
            .count();
        long inPreparation = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.IN_PREPARATION)
            .count();
        long ready = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.READY)
            .count();
        long onTheWay = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.ON_THE_WAY)
            .count();
        
        // Calculate average preparation time for completed orders today
        List<Order> completedOrders = activeOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.PAID && order.getUpdatedAt() != null)
            .collect(Collectors.toList());
        
        double avgPreparationTime = 0.0;
        if (!completedOrders.isEmpty()) {
            long totalMinutes = completedOrders.stream()
                .mapToLong(order -> Duration.between(order.getCreatedAt(), order.getUpdatedAt()).toMinutes())
                .sum();
            avgPreparationTime = (double) totalMinutes / completedOrders.size();
        }
        
        return new PendingOrdersDTO(
            pending,
            inPreparation,
            ready,
            onTheWay,
            avgPreparationTime
        );
    }

    private List<ReservationDTO> getTodayReservations() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        
        // Get all reservations for today
        List<Reservation> todayReservations = reservationRepository.findByReservationDateOrderByReservationTimeAsc(today);
        
        // Filter active reservations (not cancelled or no-show)
        List<Reservation> activeReservations = todayReservations.stream()
            .filter(reservation -> {
                return reservation.getStatus() != ReservationStatus.CANCELLED &&
                       reservation.getStatus() != ReservationStatus.NO_SHOW;
            })
            .collect(Collectors.toList());
        
        // If no active reservations at all, return empty list
        if (activeReservations.isEmpty()) {
            return List.of();
        }
        
        // Try to get upcoming reservations (next 4 hours)
        LocalTime endTime = now.plusHours(4);
        List<Reservation> upcomingReservations = activeReservations.stream()
            .filter(reservation -> {
                LocalTime resTime = reservation.getReservationTime();
                return resTime.isAfter(now) && resTime.isBefore(endTime);
            })
            .collect(Collectors.toList());
        
        // If no upcoming reservations, show all active reservations for today
        List<Reservation> reservationsToShow = upcomingReservations.isEmpty() 
            ? activeReservations 
            : upcomingReservations;
        
        // Map to DTO
        return reservationsToShow.stream()
            .sorted(Comparator.comparing(Reservation::getReservationTime))
            .limit(6) // Show max 6 reservations
            .map(reservation -> {
                String status = formatReservationStatus(reservation.getStatus());
                String statusColor = getReservationStatusColor(reservation.getStatus());
                String tableNumber = reservation.getRestaurantTable() != null ? 
                    String.valueOf(reservation.getRestaurantTable().getTableNumber()) : "Por asignar";
                
                return new ReservationDTO(
                    reservation.getId(),
                    reservation.getCustomerName(),
                    reservation.getReservationTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    reservation.getNumberOfGuests(),
                    tableNumber,
                    status,
                    statusColor
                );
            })
            .collect(Collectors.toList());
    }

    private String formatReservationStatus(ReservationStatus status) {
        switch (status) {
            case RESERVED: return "Confirmada";
            case OCCUPIED: return "En mesa";
            case COMPLETED: return "Completada";
            default: return status.name();
        }
    }

    private String getReservationStatusColor(ReservationStatus status) {
        switch (status) {
            case RESERVED: return "bg-blue-100 text-blue-800";
            case OCCUPIED: return "bg-green-100 text-green-800";
            case COMPLETED: return "bg-gray-100 text-gray-800";
            default: return "bg-gray-100 text-gray-800";
        }
    }
    
    @Override
    public List<PopularItemDTO> getPopularItemsByPeriod(String period) {
        LocalDateTime startDate;
        LocalDateTime endDate = LocalDateTime.now();
        
        switch (period.toLowerCase()) {
            case "week":
                startDate = LocalDate.now().minusWeeks(1).atStartOfDay();
                break;
            case "month":
                startDate = LocalDate.now().minusMonths(1).atStartOfDay();
                break;
            case "today":
            default:
                startDate = LocalDate.now().atStartOfDay();
                break;
        }
        
        // Get orders for the period
        List<Order> orders = orderRepository.findByDateRange(startDate, endDate);
        
        return getPopularItems(orders);
    }
}
