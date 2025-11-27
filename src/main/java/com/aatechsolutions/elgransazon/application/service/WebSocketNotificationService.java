package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.presentation.dto.KitchenStatsDTO;
import com.aatechsolutions.elgransazon.presentation.dto.OrderNotificationDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Service for sending real-time WebSocket notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Notifies all chefs about a new order
     */
    public void notifyNewOrder(Order order) {
        OrderNotificationDTO notification = buildOrderNotification(order, "NEW_ORDER", 
            "Nuevo pedido #" + order.getOrderNumber());
        
        // Send to all connected chefs
        messagingTemplate.convertAndSend("/topic/chef/orders", notification);
        
        // Send to admin kitchen view
        messagingTemplate.convertAndSend("/topic/admin/kitchen", notification);
        
        log.info("WebSocket: New order notification sent - {}", order.getOrderNumber());
    }

    /**
     * Notifies about order status change
     */
    public void notifyOrderStatusChange(Order order, String message) {
        OrderNotificationDTO notification = buildOrderNotification(order, "STATUS_CHANGE", message);
        
        // Send to all chefs
        messagingTemplate.convertAndSend("/topic/chef/orders", notification);
        
        // Send to admin kitchen
        messagingTemplate.convertAndSend("/topic/admin/kitchen", notification);
        
        // If chef assigned, send personal notification
        if (order.getPreparedBy() != null) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedBy().getUsername(),
                "/queue/orders",
                notification
            );
        }
        
        log.debug("WebSocket: Order status change - {} - {}", order.getOrderNumber(), message);
    }

    /**
     * Notifies when items are added to an existing order
     */
    public void notifyItemsAdded(Order order, int itemCount) {
        String message = String.format("Se agregaron %d item(s) al pedido %s", itemCount, order.getOrderNumber());
        OrderNotificationDTO notification = buildOrderNotification(order, "ITEMS_ADDED", message);
        
        // Send to all chefs - order returns to pending
        messagingTemplate.convertAndSend("/topic/chef/orders", notification);
        
        // Send to all roles to update their views
        messagingTemplate.convertAndSend("/topic/orders", notification);
        
        // Send to admin kitchen
        messagingTemplate.convertAndSend("/topic/admin/kitchen", notification);
        
        // If chef assigned, send personal notification
        if (order.getPreparedBy() != null) {
            messagingTemplate.convertAndSendToUser(
                order.getPreparedBy().getUsername(),
                "/queue/orders",
                notification
            );
        }
        
        log.info("WebSocket: Items added notification - {} - {} items", order.getOrderNumber(), itemCount);
    }

    /**
     * Notifies when a chef is assigned to an order
     */
    public void notifyChefAssigned(Order order, String chefName) {
        OrderNotificationDTO notification = buildOrderNotification(order, "CHEF_ASSIGNED",
            "Pedido asignado a " + chefName);
        notification.setChefName(chefName);
        
        // Send to all to update kitchen view
        messagingTemplate.convertAndSend("/topic/chef/orders", notification);
        messagingTemplate.convertAndSend("/topic/admin/kitchen", notification);
        
        log.info("WebSocket: Chef assignment - {} assigned to {}", 
            order.getOrderNumber(), chefName);
    }

    /**
     * Updates kitchen statistics in real-time
     */
    public void updateKitchenStats(KitchenStatsDTO stats) {
        messagingTemplate.convertAndSend("/topic/kitchen/stats", stats);
        log.debug("WebSocket: Kitchen stats updated - pending={}, inPrep={}", 
            stats.getPendingCount(), stats.getInPreparationCount());
    }

    /**
     * Sends notification to administrators
     */
    public void notifyAdmins(String message, Object data) {
        AdminNotification notification = new AdminNotification(message, data);
        messagingTemplate.convertAndSend("/topic/admin/notifications", notification);
        log.info("WebSocket: Admin notification sent - {}", message);
    }

    /**
     * Notifies about order deletion
     */
    public void notifyOrderDeleted(Long orderId, String orderNumber) {
        OrderDeletionNotification notification = new OrderDeletionNotification(orderId, orderNumber);
        messagingTemplate.convertAndSend("/topic/chef/orders", notification);
        messagingTemplate.convertAndSend("/topic/admin/kitchen", notification);
        log.info("WebSocket: Order deletion notification - {}", orderNumber);
    }

    // Helper method to build order notification DTO
    private OrderNotificationDTO buildOrderNotification(Order order, String type, String message) {
        return OrderNotificationDTO.builder()
            .orderId(order.getIdOrder())
            .orderNumber(order.getOrderNumber())
            .status(order.getStatus())
            .orderType(order.getOrderType())
            .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
            .total(order.getTotal())
            .createdAt(order.getCreatedAt())
            .itemCount(order.getOrderDetails() != null ? order.getOrderDetails().size() : 0)
            .items(order.getOrderDetails() != null ? order.getOrderDetails().stream()
                .map(detail -> OrderNotificationDTO.OrderItemDTO.builder()
                    .name(detail.getItemMenu().getName())
                    .quantity(detail.getQuantity())
                    .requiresPreparation(detail.getItemMenu().getRequiresPreparation())
                    .build())
                .collect(Collectors.toList()) : null)
            .notificationType(type)
            .message(message)
            .chefName(order.getPreparedBy() != null ? 
                order.getPreparedBy().getNombre() + " " + order.getPreparedBy().getApellido() : null)
            .build();
    }

    // Inner classes for specific notification types
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class AdminNotification {
        private String message;
        private Object data;
    }

    @lombok.Data
    private static class OrderDeletionNotification {
        private Long orderId;
        private String orderNumber;
        private String notificationType = "ORDER_DELETED";

        public OrderDeletionNotification(Long orderId, String orderNumber) {
            this.orderId = orderId;
            this.orderNumber = orderNumber;
        }
    }
}
