package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Service for generating PDF tickets for orders
 * Optimized for thermal printers (58mm or 80mm)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketPdfService {

    private final SystemConfigurationService systemConfigurationService;

    // Ticket width in points (58mm = 164 points, 80mm = 226 points)
    private static final float TICKET_WIDTH = 226f; // 80mm
    private static final float MARGIN = 10f;

    /**
     * Generate PDF ticket for an order
     * @param order Order to generate ticket for
     * @return byte array containing the PDF
     */
    public byte[] generateTicket(Order order) throws IOException {
        log.info("Generating PDF ticket for order: {}", order.getOrderNumber());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Create PDF with custom page size (ticket size)
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        
        // Calculate page height dynamically based on content
        float estimatedHeight = calculateEstimatedHeight(order);
        PageSize pageSize = new PageSize(TICKET_WIDTH, estimatedHeight);
        pdfDoc.setDefaultPageSize(pageSize);
        
        Document document = new Document(pdfDoc);
        document.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);

        // Get system configuration
        SystemConfiguration config = systemConfigurationService.getConfiguration();

        // Create fonts
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Add logo (centered)
        try {
            ClassPathResource imgResource = new ClassPathResource("static/images/LogoVariante.png");
            Image logo = new Image(ImageDataFactory.create(imgResource.getURL()));
            logo.setWidth(60);
            logo.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
            document.add(logo);
        } catch (Exception e) {
            log.warn("Could not load logo image: {}", e.getMessage());
        }

        // Restaurant name (centered, bold)
        Paragraph restaurantName = new Paragraph(config.getRestaurantName())
                .setFont(boldFont)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5);
        document.add(restaurantName);

        // Address (centered)
        Paragraph address = new Paragraph(config.getAddress())
                .setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2);
        document.add(address);

        // Phone (centered)
        Paragraph phone = new Paragraph("Tel: " + config.getPhone())
                .setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2);
        document.add(phone);

        // Separator line
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5)
                .setMarginBottom(5));

        // Order number (bold, larger)
        Paragraph orderNum = new Paragraph("ORDEN: " + order.getOrderNumber())
                .setFont(boldFont)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(orderNum);

        // Items separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(3)
                .setMarginBottom(3));

        // Items header
        Paragraph itemsHeader = new Paragraph("DETALLE DEL PEDIDO")
                .setFont(boldFont)
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(itemsHeader);

        // Order items table
        Table itemsTable = new Table(new float[]{3, 1, 2});
        itemsTable.setWidth(UnitValue.createPercentValue(100));
        itemsTable.setBorder(Border.NO_BORDER);

        for (OrderDetail detail : order.getOrderDetails()) {
            // Item name and quantity
            String itemName = detail.getItemMenu().getName();
            Integer quantity = detail.getQuantity();
            
            Cell nameCell = new Cell()
                    .add(new Paragraph(itemName)
                            .setFont(normalFont)
                            .setFontSize(8))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(2);
            
            Cell qtyCell = new Cell()
                    .add(new Paragraph(quantity.toString())
                            .setFont(normalFont)
                            .setFontSize(8)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(2);
            
            Cell priceCell = new Cell()
                    .add(new Paragraph("$" + detail.getSubtotal().toString())
                            .setFont(normalFont)
                            .setFontSize(8)
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER)
                    .setPadding(2);
            
            itemsTable.addCell(nameCell);
            itemsTable.addCell(qtyCell);
            itemsTable.addCell(priceCell);

            // Add comments if any
            if (detail.getComments() != null && !detail.getComments().trim().isEmpty()) {
                Cell commentCell = new Cell(1, 3)
                        .add(new Paragraph("  → " + detail.getComments())
                                .setFont(normalFont)
                                .setFontSize(7)
                                .setFontColor(ColorConstants.DARK_GRAY))
                        .setBorder(Border.NO_BORDER)
                        .setPadding(0)
                        .setPaddingLeft(5);
                itemsTable.addCell(commentCell);
            }
        }

        document.add(itemsTable);

        // Totals separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(3)
                .setMarginBottom(3));

        // Subtotal
        Table totalsTable = new Table(new float[]{3, 2});
        totalsTable.setWidth(UnitValue.createPercentValue(100));
        totalsTable.setBorder(Border.NO_BORDER);

        addTotalRow(totalsTable, "Subtotal:", "$" + order.getSubtotal().toString(), normalFont, boldFont, false);
        addTotalRow(totalsTable, "IVA (" + order.getTaxRate() + "%):", "$" + order.getTaxAmount().toString(), normalFont, boldFont, false);

        // Tip (if any)
        if (order.getTip() != null && order.getTip().compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totalsTable, "Propina:", "$" + order.getTip().toString(), normalFont, boldFont, false);
        }

        // Total (bold)
        addTotalRow(totalsTable, "TOTAL:", "$" + order.getTotalWithTip().toString(), boldFont, boldFont, true);

        document.add(totalsTable);

        // Order type and payment method in one line
        Paragraph orderInfoParagraph = new Paragraph()
                .add(new Text("Tipo: ").setFont(boldFont))
                .add(new Text(order.getOrderType().getDisplayName()).setFont(normalFont))
                .add(new Text(" | ").setFont(normalFont))
                .add(new Text("Pago: ").setFont(boldFont))
                .add(new Text(order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "N/A").setFont(normalFont))
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5);
        document.add(orderInfoParagraph);

        // Customer name (if available)
        if (order.getCustomerName() != null && !order.getCustomerName().trim().isEmpty()) {
            Paragraph customer = new Paragraph()
                    .add(new Text("Cliente: ").setFont(boldFont))
                    .add(new Text(order.getCustomerName()).setFont(normalFont))
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(customer);
        }

        // Served by
        String servedBy = order.getEmployee() != null ? order.getEmployee().getFullName() : "Sistema";
        Paragraph employee = new Paragraph()
                .add(new Text("Atendido por: ").setFont(boldFont))
                .add(new Text(servedBy).setFont(normalFont))
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(employee);

        // Date and time separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5)
                .setMarginBottom(3));

        // Date and time
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        Paragraph dateTime = new Paragraph(order.getCreatedAt().format(formatter))
                .setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(dateTime);

        // Final separator
        document.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5)
                .setMarginBottom(5));

        // Thank you message
        Paragraph thankYou = new Paragraph("¡Gracias por su preferencia!")
                .setFont(boldFont)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(5);
        document.add(thankYou);

        Paragraph visitAgain = new Paragraph("Esperamos volver a atenderle pronto")
                .setFont(normalFont)
                .setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(2)
                .setMarginBottom(10);
        document.add(visitAgain);

        document.close();

        log.info("PDF ticket generated successfully for order: {}", order.getOrderNumber());
        return baos.toByteArray();
    }

    /**
     * Add a row to the totals table
     */
    private void addTotalRow(Table table, String label, String value, PdfFont valueFont, PdfFont labelFont, boolean isBold) {
        int fontSize = isBold ? 10 : 8;
        
        Cell labelCell = new Cell()
                .add(new Paragraph(label)
                        .setFont(labelFont)
                        .setFontSize(fontSize))
                .setBorder(Border.NO_BORDER)
                .setPadding(2)
                .setTextAlignment(TextAlignment.RIGHT);
        
        Cell valueCell = new Cell()
                .add(new Paragraph(value)
                        .setFont(valueFont)
                        .setFontSize(fontSize))
                .setBorder(Border.NO_BORDER)
                .setPadding(2)
                .setTextAlignment(TextAlignment.RIGHT);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    /**
     * Calculate estimated height for the PDF page
     */
    private float calculateEstimatedHeight(Order order) {
        float baseHeight = 400f; // Base height for header, footer, etc.
        float itemHeight = 15f; // Height per item
        float commentHeight = 10f; // Height for comments
        
        for (OrderDetail detail : order.getOrderDetails()) {
            baseHeight += itemHeight;
            if (detail.getComments() != null && !detail.getComments().trim().isEmpty()) {
                baseHeight += commentHeight;
            }
        }
        
        return baseHeight;
    }
}
