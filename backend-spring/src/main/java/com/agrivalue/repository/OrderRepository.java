package com.agrivalue.repository;

import com.agrivalue.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByBuyerIdOrderByCreatedAtDesc(Integer buyerId);
    Optional<Order> findByIdAndBuyerId(Integer id, Integer buyerId);
    List<Order> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.paymentStatus = 'paid'")
    BigDecimal getTotalRevenue();
}
