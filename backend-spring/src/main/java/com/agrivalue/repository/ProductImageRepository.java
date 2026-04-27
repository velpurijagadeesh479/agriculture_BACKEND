package com.agrivalue.repository;

import com.agrivalue.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
    List<ProductImage> findByProductId(Integer productId);
    Optional<ProductImage> findFirstByProductIdAndIsPrimaryTrue(Integer productId);
    void deleteByProductId(Integer productId);
}
