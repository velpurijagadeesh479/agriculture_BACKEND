package com.agrivalue.repository;

import com.agrivalue.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
    List<CartItem> findByBuyerIdOrderByCreatedAtDesc(Integer buyerId);
    Optional<CartItem> findByBuyerIdAndProductId(Integer buyerId, Integer productId);
    Optional<CartItem> findByIdAndBuyerId(Integer id, Integer buyerId);
    void deleteByBuyerId(Integer buyerId);
}
