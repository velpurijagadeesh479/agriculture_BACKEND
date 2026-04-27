package com.agrivalue.repository;

import com.agrivalue.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    List<OrderItem> findByOrderId(Integer orderId);
    List<OrderItem> findByFarmerIdOrderByCreatedAtDesc(Integer farmerId);
    List<OrderItem> findByOrderIdAndFarmerId(Integer orderId, Integer farmerId);
    long countByFarmerId(Integer farmerId);
    long countByFarmerIdAndStatus(Integer farmerId, com.agrivalue.entity.Order.OrderStatus status);

    @Query("SELECT COALESCE(SUM(oi.priceAtPurchase * oi.quantity), 0) FROM OrderItem oi JOIN Order o ON oi.orderId = o.id WHERE oi.farmerId = :farmerId AND o.paymentStatus = 'paid'")
    BigDecimal getTotalEarningsByFarmerId(Integer farmerId);

    @Query("SELECT COALESCE(SUM(oi.priceAtPurchase * oi.quantity), 0) FROM OrderItem oi JOIN Order o ON oi.orderId = o.id WHERE oi.farmerId = :farmerId AND o.paymentStatus = 'pending'")
    BigDecimal getPendingEarningsByFarmerId(Integer farmerId);
}
