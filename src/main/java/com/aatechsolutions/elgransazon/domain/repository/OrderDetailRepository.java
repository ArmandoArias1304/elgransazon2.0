package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.OrderDetail;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for OrderDetail entity
 */
@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    /**
     * Find all order details by order
     */
    List<OrderDetail> findByOrder(Order order);

    /**
     * Find all order details by order ID
     */
    @Query("SELECT od FROM OrderDetail od WHERE od.order.idOrder = :orderId")
    List<OrderDetail> findByOrderId(@Param("orderId") Long orderId);

    /**
     * Find all order details by item menu
     */
    List<OrderDetail> findByItemMenu(ItemMenu itemMenu);

    /**
     * Find all order details by item menu ID
     */
    @Query("SELECT od FROM OrderDetail od WHERE od.itemMenu.idItemMenu = :itemMenuId")
    List<OrderDetail> findByItemMenuId(@Param("itemMenuId") Long itemMenuId);

    /**
     * Delete all order details by order
     */
    void deleteByOrder(Order order);
}
