package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.ReportPdfService;
import com.aatechsolutions.elgransazon.domain.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Reports (Reportes)
 * Generates reports based on PAID orders
 * Accessible by ADMIN, MANAGER, and WAITER roles
 */
@Controller
@RequestMapping("/admin/reports")
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_WAITER')")
@Slf4j
public class ReportsController {

    private final OrderService orderService;
    private final ReportPdfService reportPdfService;

    // Constructor manual para inyectar adminOrderService específicamente
    public ReportsController(
            @Qualifier("adminOrderService") OrderService orderService,
            ReportPdfService reportPdfService) {
        this.orderService = orderService;
        this.reportPdfService = reportPdfService;
    }

    /**
     * Show reports view with data
     */
    @GetMapping
    public String showReports(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        
        log.debug("Displaying reports - startDate: {}, endDate: {}", startDate, endDate);

        // Get all PAID orders
        List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);

        // Apply date filter
        if (startDate != null && !startDate.isEmpty()) {
            paidOrders = filterByDateRange(paidOrders, startDate, endDate);
        }

        // Sort by payment date (most recent first)
        paidOrders = paidOrders.stream()
            .sorted((o1, o2) -> {
                LocalDateTime date1 = o1.getUpdatedAt() != null ? o1.getUpdatedAt() : o1.getCreatedAt();
                LocalDateTime date2 = o2.getUpdatedAt() != null ? o2.getUpdatedAt() : o2.getCreatedAt();
                return date2.compareTo(date1);
            })
            .collect(Collectors.toList());

        // Calculate statistics
        BigDecimal totalSales = calculateTotalSales(paidOrders);
        long totalOrders = paidOrders.size();
        
        // Calculate sales by category
        Map<String, BigDecimal> salesByCategory = calculateSalesByCategory(paidOrders);
        
        // Calculate sales by employee
        Map<String, BigDecimal> salesByEmployee = calculateSalesByEmployee(paidOrders);
        
        // Calculate sales by payment method
        Map<String, Long> ordersByPaymentMethod = calculateOrdersByPaymentMethod(paidOrders);
        
        // Top 10 best selling items
        List<Map<String, Object>> topSellingItems = calculateTopSellingItems(paidOrders, 10);

        model.addAttribute("totalSales", totalSales);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("salesByCategory", salesByCategory);
        model.addAttribute("salesByEmployee", salesByEmployee);
        model.addAttribute("ordersByPaymentMethod", ordersByPaymentMethod);
        model.addAttribute("topSellingItems", topSellingItems);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/reports/list";
    }

    /**
     * Filter orders by date range
     */
    private List<Order> filterByDateRange(List<Order> orders, String startDate, String endDate) {
        LocalDateTime startDateTime = null;
        LocalDateTime endDateTime = null;

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            if (startDate != null && !startDate.isEmpty()) {
                startDateTime = LocalDate.parse(startDate, formatter).atStartOfDay();
            }
            
            if (endDate != null && !endDate.isEmpty()) {
                endDateTime = LocalDate.parse(endDate, formatter).atTime(23, 59, 59);
            } else if (startDateTime != null) {
                endDateTime = startDateTime.toLocalDate().atTime(23, 59, 59);
            }
        } catch (Exception e) {
            log.error("Error parsing date range: {} - {}", startDate, endDate, e);
            return orders;
        }

        if (startDateTime == null && endDateTime == null) {
            return orders;
        }

        final LocalDateTime finalStartDateTime = startDateTime;
        final LocalDateTime finalEndDateTime = endDateTime;

        return orders.stream()
            .filter(order -> {
                LocalDateTime orderDate = order.getUpdatedAt() != null ? 
                    order.getUpdatedAt() : order.getCreatedAt();
                
                if (orderDate == null) return false;
                
                boolean afterStart = finalStartDateTime == null || !orderDate.isBefore(finalStartDateTime);
                boolean beforeEnd = finalEndDateTime == null || !orderDate.isAfter(finalEndDateTime);
                
                return afterStart && beforeEnd;
            })
            .collect(Collectors.toList());
    }

    /**
     * Calculate total sales amount (subtotal + tax, WITHOUT tip)
     */
    private BigDecimal calculateTotalSales(List<Order> orders) {
        return orders.stream()
            .map(order -> order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate sales by category (with tax, WITHOUT tip)
     */
    private Map<String, BigDecimal> calculateSalesByCategory(List<Order> orders) {
        Map<String, BigDecimal> salesByCategory = new HashMap<>();
        
        for (Order order : orders) {
            // Get tax rate for this order
            BigDecimal taxRate = order.getTaxRate() != null ? order.getTaxRate() : BigDecimal.ZERO;
            
            for (OrderDetail detail : order.getOrderDetails()) {
                String categoryName = detail.getItemMenu().getCategory().getName();
                BigDecimal itemSubtotal = detail.getSubtotal();
                
                // Calculate tax for this item
                BigDecimal itemTax = itemSubtotal
                    .multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                
                // Total = subtotal + tax
                BigDecimal itemTotalWithTax = itemSubtotal.add(itemTax);
                
                salesByCategory.merge(categoryName, itemTotalWithTax, BigDecimal::add);
            }
        }
        
        return salesByCategory;
    }

    /**
     * Calculate sales by employee (total with tax, WITHOUT tip)
     */
    private Map<String, BigDecimal> calculateSalesByEmployee(List<Order> orders) {
        Map<String, BigDecimal> salesByEmployee = new HashMap<>();
        
        for (Order order : orders) {
            if (order.getEmployee() != null) {
                String employeeName = order.getEmployee().getNombre() + " " + order.getEmployee().getApellido();
                // Use total (subtotal + tax) without tip
                BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO;
                
                salesByEmployee.merge(employeeName, orderTotal, BigDecimal::add);
            }
        }
        
        return salesByEmployee;
    }

    /**
     * Calculate orders by payment method
     */
    private Map<String, Long> calculateOrdersByPaymentMethod(List<Order> orders) {
        return orders.stream()
            .filter(order -> order.getPaymentMethod() != null)
            .collect(Collectors.groupingBy(
                order -> order.getPaymentMethod().getDisplayName(),
                Collectors.counting()
            ));
    }

    /**
     * Calculate top selling items (with tax, WITHOUT tip)
     */
    private List<Map<String, Object>> calculateTopSellingItems(List<Order> orders, int limit) {
        Map<Long, Map<String, Object>> itemSales = new HashMap<>();
        
        for (Order order : orders) {
            // Get tax rate for this order
            BigDecimal taxRate = order.getTaxRate() != null ? order.getTaxRate() : BigDecimal.ZERO;
            
            for (OrderDetail detail : order.getOrderDetails()) {
                Long itemId = detail.getItemMenu().getIdItemMenu();
                
                itemSales.putIfAbsent(itemId, new HashMap<>());
                Map<String, Object> itemData = itemSales.get(itemId);
                
                if (!itemData.containsKey("name")) {
                    itemData.put("name", detail.getItemMenu().getName());
                    itemData.put("category", detail.getItemMenu().getCategory().getName());
                    itemData.put("quantity", 0);
                    itemData.put("total", BigDecimal.ZERO);
                }
                
                int currentQuantity = (int) itemData.get("quantity");
                itemData.put("quantity", currentQuantity + detail.getQuantity());
                
                // Calculate item total with tax
                BigDecimal itemSubtotal = detail.getSubtotal();
                BigDecimal itemTax = itemSubtotal
                    .multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal itemTotalWithTax = itemSubtotal.add(itemTax);
                
                BigDecimal currentTotal = (BigDecimal) itemData.get("total");
                itemData.put("total", currentTotal.add(itemTotalWithTax));
            }
        }
        
        return itemSales.values().stream()
            .sorted((a, b) -> {
                int qtyA = (int) a.get("quantity");
                int qtyB = (int) b.get("quantity");
                return Integer.compare(qtyB, qtyA);
            })
            .limit(limit)
            .collect(Collectors.toList());
    }

    // ========== PDF Download Endpoints ==========

    /**
     * Download Executive Report PDF
     */
    @GetMapping("/download/executive")
    public ResponseEntity<byte[]> downloadExecutivePdf(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Generating Executive PDF Report - startDate: {}, endDate: {}", startDate, endDate);

        try {
            // Get all PAID orders
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            
            // Apply date filter
            if (startDate != null && !startDate.isEmpty()) {
                paidOrders = filterByDateRange(paidOrders, startDate, endDate);
            }

            // Calculate statistics
            BigDecimal totalSales = calculateTotalSales(paidOrders);
            long totalOrders = paidOrders.size();
            Map<String, BigDecimal> salesByCategory = calculateSalesByCategory(paidOrders);
            Map<String, BigDecimal> salesByEmployee = calculateSalesByEmployee(paidOrders);
            Map<String, Long> ordersByPaymentMethod = calculateOrdersByPaymentMethod(paidOrders);
            List<Map<String, Object>> topSellingItems = calculateTopSellingItems(paidOrders, 10);

            // Generate PDF
            byte[] pdfBytes = reportPdfService.generateExecutiveReport(
                paidOrders, startDate, endDate, totalSales, totalOrders,
                salesByCategory, salesByEmployee, ordersByPaymentMethod, topSellingItems
            );

            // Prepare response
            String filename = "Reporte_Ejecutivo_" + getCurrentDateForFilename() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating Executive PDF report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download Products Report PDF
     */
    @GetMapping("/download/products")
    public ResponseEntity<byte[]> downloadProductsPdf(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Generating Products PDF Report - startDate: {}, endDate: {}", startDate, endDate);

        try {
            // Get all PAID orders
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            
            // Apply date filter
            if (startDate != null && !startDate.isEmpty()) {
                paidOrders = filterByDateRange(paidOrders, startDate, endDate);
            }

            // Calculate top selling items
            List<Map<String, Object>> topSellingItems = calculateTopSellingItems(paidOrders, 50);

            // Generate PDF
            byte[] pdfBytes = reportPdfService.generateProductsReport(
                paidOrders, startDate, endDate, topSellingItems
            );

            // Prepare response
            String filename = "Reporte_Productos_" + getCurrentDateForFilename() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating Products PDF report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download Employees Report PDF
     */
    @GetMapping("/download/employees")
    public ResponseEntity<byte[]> downloadEmployeesPdf(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Generating Employees PDF Report - startDate: {}, endDate: {}", startDate, endDate);

        try {
            // Get all PAID orders
            List<Order> paidOrders = orderService.findByStatus(OrderStatus.PAID);
            
            // Apply date filter
            if (startDate != null && !startDate.isEmpty()) {
                paidOrders = filterByDateRange(paidOrders, startDate, endDate);
            }

            // Calculate statistics
            BigDecimal totalSales = calculateTotalSales(paidOrders);
            Map<String, BigDecimal> salesByEmployee = calculateSalesByEmployee(paidOrders);

            // Generate PDF
            byte[] pdfBytes = reportPdfService.generateEmployeesReport(
                paidOrders, startDate, endDate, salesByEmployee, totalSales
            );

            // Prepare response
            String filename = "Reporte_Empleados_" + getCurrentDateForFilename() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

        } catch (Exception e) {
            log.error("Error generating Employees PDF report", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get current date formatted for filename
     */
    private String getCurrentDateForFilename() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return LocalDateTime.now().format(formatter);
    }
}

