package com.agrivalue.repository;

import com.agrivalue.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Integer> {
    List<WishlistItem> findByBuyerIdOrderByCreatedAtDesc(Integer buyerId);
    Optional<WishlistItem> findByBuyerIdAndProductId(Integer buyerId, Integer productId);
    void deleteByBuyerIdAndProductId(Integer buyerId, Integer productId);
}
