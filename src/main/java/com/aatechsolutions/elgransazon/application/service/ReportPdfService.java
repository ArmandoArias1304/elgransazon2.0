package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating PDF reports
 * Generates 3 types of reports: Executive, Products, and Employees
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportPdfService {

    private final SystemConfigurationService systemConfigurationService;

    // Color palette - matching your theme
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(56, 224, 123); // #38e07b
    private static final DeviceRgb PRIMARY_DARK = new DeviceRgb(43, 200, 102); // #2bc866
    private static final DeviceRgb DARK_COLOR = new DeviceRgb(45, 45, 45);
    private static final DeviceRgb GRAY_COLOR = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb LIGHT_GRAY = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);

    /**
     * Generate Executive Report (All-in-one summary)
     */
    public byte[] generateExecutiveReport(
            java.util.List<Order> paidOrders,
            String startDate,
            String endDate,
            BigDecimal totalSales,
            long totalOrders,
            Map<String, BigDecimal> salesByCategory,
            Map<String, BigDecimal> salesByEmployee,
            Map<String, Long> ordersByPaymentMethod,
            java.util.List<Map<String, Object>> topSellingItems) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.LETTER);
        document.setMargins(40, 40, 40, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Header
        addHeader(document, boldFont, regularFont, "REPORTE EJECUTIVO DE VENTAS");
        addDateRange(document, regularFont, startDate, endDate);
        document.add(new Paragraph("\n"));

        // Summary Section
        addSectionTitle(document, boldFont, "Resumen General");
        Table summaryTable = new Table(new float[]{1, 1, 1});
        summaryTable.setWidth(UnitValue.createPercentValue(100));
        
        addSummaryCell(summaryTable, boldFont, regularFont, "Total de Ventas", 
            String.format("$%,.2f", totalSales));
        addSummaryCell(summaryTable, boldFont, regularFont, "Órdenes Pagadas", 
            String.valueOf(totalOrders));
        addSummaryCell(summaryTable, boldFont, regularFont, "Ticket Promedio", 
            totalOrders > 0 ? String.format("$%,.2f", totalSales.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)) : "$0.00");
        
        document.add(summaryTable);
        document.add(new Paragraph("\n"));

        // Top 5 Products
        addSectionTitle(document, boldFont, "Top 5 Productos Más Vendidos");
        Table productsTable = new Table(new float[]{3, 1, 1, 2});
        productsTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(productsTable, boldFont, "Producto", "Cant.", "Cat.", "Total");
        
        topSellingItems.stream().limit(5).forEach(item -> {
            addTableRow(productsTable, regularFont,
                item.get("name").toString(),
                item.get("quantity").toString(),
                item.get("category").toString(),
                String.format("$%,.2f", item.get("total"))
            );
        });
        document.add(productsTable);
        document.add(new Paragraph("\n"));

        // Sales by Category
        addSectionTitle(document, boldFont, "Ventas por Categoría");
        Table categoryTable = new Table(new float[]{3, 2, 2});
        categoryTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(categoryTable, boldFont, "Categoría", "Total", "% Part.");
        
        salesByCategory.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .forEach(entry -> {
                BigDecimal percentage = totalSales.compareTo(BigDecimal.ZERO) > 0
                    ? entry.getValue().multiply(BigDecimal.valueOf(100)).divide(totalSales, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
                addTableRow(categoryTable, regularFont,
                    entry.getKey(),
                    String.format("$%,.2f", entry.getValue()),
                    String.format("%.2f%%", percentage)
                );
            });
        document.add(categoryTable);
        document.add(new Paragraph("\n"));

        // Sales by Employee
        if (!salesByEmployee.isEmpty()) {
            addSectionTitle(document, boldFont, "Ventas por Empleado");
            Table employeeTable = new Table(new float[]{4, 2, 1});
            employeeTable.setWidth(UnitValue.createPercentValue(100));
            addTableHeader(employeeTable, boldFont, "Empleado", "Total Ventas", "Órdenes");
            
            Map<String, Long> ordersByEmployee = paidOrders.stream()
                .filter(o -> o.getEmployee() != null)
                .collect(Collectors.groupingBy(
                    o -> o.getEmployee().getNombre() + " " + o.getEmployee().getApellido(),
                    Collectors.counting()
                ));
            
            salesByEmployee.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> {
                    addTableRow(employeeTable, regularFont,
                        entry.getKey(),
                        String.format("$%,.2f", entry.getValue()),
                        String.valueOf(ordersByEmployee.getOrDefault(entry.getKey(), 0L))
                    );
                });
            document.add(employeeTable);
            document.add(new Paragraph("\n"));
        }

        // Payment Methods
        addSectionTitle(document, boldFont, "Métodos de Pago");
        Table paymentTable = new Table(new float[]{3, 2, 2});
        paymentTable.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(paymentTable, boldFont, "Método", "Órdenes", "% Part.");
        
        ordersByPaymentMethod.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                double percentage = totalOrders > 0 
                    ? (entry.getValue() * 100.0 / totalOrders) 
                    : 0.0;
                addTableRow(paymentTable, regularFont,
                    entry.getKey(),
                    String.valueOf(entry.getValue()),
                    String.format("%.2f%%", percentage)
                );
            });
        document.add(paymentTable);

        // Footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Generate Products Report (Top selling products detailed)
     */
    public byte[] generateProductsReport(
            java.util.List<Order> paidOrders,
            String startDate,
            String endDate,
            java.util.List<Map<String, Object>> topSellingItems) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.LETTER);
        document.setMargins(40, 40, 40, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Header
        addHeader(document, boldFont, regularFont, "REPORTE DE PRODUCTOS MÁS VENDIDOS");
        addDateRange(document, regularFont, startDate, endDate);
        document.add(new Paragraph("\n"));

        // Summary
        BigDecimal totalProductSales = topSellingItems.stream()
            .map(item -> (BigDecimal) item.get("total"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        int totalQuantity = topSellingItems.stream()
            .mapToInt(item -> (Integer) item.get("quantity"))
            .sum();

        addSectionTitle(document, boldFont, "Resumen");
        Table summaryTable = new Table(new float[]{1, 1, 1});
        summaryTable.setWidth(UnitValue.createPercentValue(100));
        
        addSummaryCell(summaryTable, boldFont, regularFont, "Total Productos Vendidos", 
            String.valueOf(totalQuantity));
        addSummaryCell(summaryTable, boldFont, regularFont, "Variedades Diferentes", 
            String.valueOf(topSellingItems.size()));
        addSummaryCell(summaryTable, boldFont, regularFont, "Ingresos Generados", 
            String.format("$%,.2f", totalProductSales));
        
        document.add(summaryTable);
        document.add(new Paragraph("\n"));

        // Products Table
        addSectionTitle(document, boldFont, "Detalle de Productos");
        Table table = new Table(new float[]{0.5f, 3, 2, 1, 2, 2});
        table.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(table, boldFont, "#", "Producto", "Categoría", "Cant.", "Total", "% Part.");
        
        int rank = 1;
        for (Map<String, Object> item : topSellingItems) {
            BigDecimal itemTotal = (BigDecimal) item.get("total");
            double percentage = totalProductSales.compareTo(BigDecimal.ZERO) > 0
                ? itemTotal.multiply(BigDecimal.valueOf(100)).divide(totalProductSales, 2, java.math.RoundingMode.HALF_UP).doubleValue()
                : 0.0;
            
            addTableRow(table, regularFont,
                String.valueOf(rank++),
                item.get("name").toString(),
                item.get("category").toString(),
                item.get("quantity").toString(),
                String.format("$%,.2f", itemTotal),
                String.format("%.2f%%", percentage)
            );
        }
        document.add(table);

        // Footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Generate Employees Report (Employee performance)
     */
    public byte[] generateEmployeesReport(
            java.util.List<Order> paidOrders,
            String startDate,
            String endDate,
            Map<String, BigDecimal> salesByEmployee,
            BigDecimal totalSales) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.LETTER);
        document.setMargins(40, 40, 40, 40);

        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Header
        addHeader(document, boldFont, regularFont, "REPORTE DE DESEMPEÑO POR EMPLEADO");
        addDateRange(document, regularFont, startDate, endDate);
        document.add(new Paragraph("\n"));

        // Calculate employee statistics
        Map<String, Long> ordersByEmployee = paidOrders.stream()
            .filter(o -> o.getEmployee() != null)
            .collect(Collectors.groupingBy(
                o -> o.getEmployee().getNombre() + " " + o.getEmployee().getApellido(),
                Collectors.counting()
            ));

        // Summary
        addSectionTitle(document, boldFont, "Resumen General");
        Table summaryTable = new Table(new float[]{1, 1, 1});
        summaryTable.setWidth(UnitValue.createPercentValue(100));
        
        addSummaryCell(summaryTable, boldFont, regularFont, "Empleados Activos", 
            String.valueOf(salesByEmployee.size()));
        addSummaryCell(summaryTable, boldFont, regularFont, "Total Ventas", 
            String.format("$%,.2f", totalSales));
        addSummaryCell(summaryTable, boldFont, regularFont, "Promedio por Empleado", 
            salesByEmployee.size() > 0 ? String.format("$%,.2f", totalSales.divide(BigDecimal.valueOf(salesByEmployee.size()), 2, java.math.RoundingMode.HALF_UP)) : "$0.00");
        
        document.add(summaryTable);
        document.add(new Paragraph("\n"));

        // Employees Table
        addSectionTitle(document, boldFont, "Detalle por Empleado");
        Table table = new Table(new float[]{0.5f, 3, 2, 1, 2, 2});
        table.setWidth(UnitValue.createPercentValue(100));
        addTableHeader(table, boldFont, "#", "Empleado", "Total Ventas", "Órdenes", "Promedio", "% Part.");
        
        java.util.List<Map.Entry<String, BigDecimal>> sortedEmployees = salesByEmployee.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .collect(Collectors.toList());

        int rank = 1;
        for (Map.Entry<String, BigDecimal> entry : sortedEmployees) {
            String employeeName = entry.getKey();
            BigDecimal employeeSales = entry.getValue();
            long orders = ordersByEmployee.getOrDefault(employeeName, 0L);
            BigDecimal avgPerOrder = orders > 0 
                ? employeeSales.divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            double percentage = totalSales.compareTo(BigDecimal.ZERO) > 0
                ? employeeSales.multiply(BigDecimal.valueOf(100)).divide(totalSales, 2, java.math.RoundingMode.HALF_UP).doubleValue()
                : 0.0;

            addTableRow(table, regularFont,
                String.valueOf(rank++),
                employeeName,
                String.format("$%,.2f", employeeSales),
                String.valueOf(orders),
                String.format("$%,.2f", avgPerOrder),
                String.format("%.2f%%", percentage)
            );
        }
        document.add(table);

        // Footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    // ========== Helper Methods ==========

    private void addHeader(Document document, PdfFont boldFont, PdfFont regularFont, String title) {
        SystemConfiguration config = systemConfigurationService.getConfiguration();
        
        // Restaurant name with modern styling
        Paragraph restaurantName = new Paragraph(config.getRestaurantName())
            .setFont(boldFont)
            .setFontSize(24)
            .setFontColor(PRIMARY_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setBold()
            .setMarginBottom(2);
        document.add(restaurantName);

        // Subtitle line
        Paragraph subtitle = new Paragraph("Sistema de Reportes")
            .setFont(regularFont)
            .setFontSize(9)
            .setFontColor(GRAY_COLOR)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(15);
        document.add(subtitle);

        // Report title with background
        Table titleTable = new Table(1);
        titleTable.setWidth(UnitValue.createPercentValue(100));
        
        Cell titleCell = new Cell()
            .add(new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(WHITE)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(PRIMARY_COLOR)
            .setPadding(12)
            .setBorder(Border.NO_BORDER)
            .setMarginBottom(5);
        
        titleTable.addCell(titleCell);
        document.add(titleTable);
    }

    private void addDateRange(Document document, PdfFont font, String startDate, String endDate) {
        String dateRange;
        if (startDate != null && !startDate.isEmpty()) {
            dateRange = "📅 Periodo: " + startDate + " al " + (endDate != null && !endDate.isEmpty() ? endDate : startDate);
        } else {
            dateRange = "📅 Periodo: Todos los registros";
        }
        
        // Date range in a subtle box
        Table dateTable = new Table(1);
        dateTable.setWidth(UnitValue.createPercentValue(100));
        
        Cell dateCell = new Cell()
            .add(new Paragraph(dateRange)
                .setFont(font)
                .setFontSize(10)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(LIGHT_GRAY)
            .setPadding(8)
            .setBorder(Border.NO_BORDER)
            .setMarginBottom(15);
        
        dateTable.addCell(dateCell);
        document.add(dateTable);
    }

    private void addSectionTitle(Document document, PdfFont boldFont, String title) {
        // Section title with left border accent
        Table sectionTable = new Table(new float[]{0.05f, 0.95f});
        sectionTable.setWidth(UnitValue.createPercentValue(100));
        
        // Accent bar
        Cell accentCell = new Cell()
            .setBackgroundColor(PRIMARY_COLOR)
            .setBorder(Border.NO_BORDER)
            .setHeight(20);
        
        // Title text
        Cell titleCell = new Cell()
            .add(new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(13)
                .setFontColor(DARK_COLOR)
                .setBold())
            .setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBorder(Border.NO_BORDER)
            .setPaddingLeft(10);
        
        sectionTable.addCell(accentCell);
        sectionTable.addCell(titleCell);
        
        document.add(sectionTable.setMarginTop(10).setMarginBottom(10));
    }

    private void addSummaryCell(Table table, PdfFont boldFont, PdfFont regularFont, String label, String value) {
        Cell cell = new Cell()
            .setBorder(Border.NO_BORDER)
            .setBackgroundColor(LIGHT_GRAY)
            .setPadding(15)
            .setMarginRight(5);
        
        // Label
        cell.add(new Paragraph(label)
            .setFont(regularFont)
            .setFontSize(9)
            .setFontColor(GRAY_COLOR)
            .setMarginBottom(8)
            .setTextAlignment(TextAlignment.CENTER));
        
        // Value
        cell.add(new Paragraph(value)
            .setFont(boldFont)
            .setFontSize(18)
            .setFontColor(PRIMARY_DARK)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER));
        
        table.addCell(cell);
    }

    private void addTableHeader(Table table, PdfFont boldFont, String... headers) {
        for (String header : headers) {
            Cell cell = new Cell()
                .add(new Paragraph(header)
                    .setFont(boldFont)
                    .setFontSize(9)
                    .setBold())
                .setBackgroundColor(PRIMARY_COLOR)
                .setFontColor(WHITE)
                .setPadding(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setBorder(Border.NO_BORDER);
            table.addHeaderCell(cell);
        }
    }

    private void addTableRow(Table table, PdfFont font, String... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = new Cell()
                .add(new Paragraph(values[i])
                    .setFont(font)
                    .setFontSize(9))
                .setPadding(8)
                .setBackgroundColor(i % 2 == 0 ? WHITE : LIGHT_GRAY)
                .setBorder(new SolidBorder(new DeviceRgb(229, 231, 235), 0.5f));
            
            // Align numbers to the right
            if (values[i].contains("$") || values[i].contains("%") || values[i].matches("\\d+")) {
                cell.setTextAlignment(TextAlignment.RIGHT);
            } else if (i == 0 && values[i].matches("\\d+")) {
                // Row number centered
                cell.setTextAlignment(TextAlignment.CENTER);
            }
            
            table.addCell(cell);
        }
    }

    private void addFooter(Document document, PdfFont font) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm:ss");
        String generatedDate = LocalDateTime.now().format(formatter);
        
        // Footer with modern design
        Table footerTable = new Table(1);
        footerTable.setWidth(UnitValue.createPercentValue(100));
        
        Cell footerCell = new Cell()
            .add(new Paragraph("📄 Reporte generado el " + generatedDate)
                .setFont(font)
                .setFontSize(8)
                .setFontColor(GRAY_COLOR)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(LIGHT_GRAY)
            .setPadding(10)
            .setBorder(Border.NO_BORDER)
            .setMarginTop(20);
        
        footerTable.addCell(footerCell);
        document.add(footerTable);
    }
}
