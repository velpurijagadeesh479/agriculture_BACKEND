package com.agrivalue.repository;

import com.agrivalue.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByFarmerIdOrderByCreatedAtDesc(Integer farmerId);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND p.quantity > 0")
    Page<Product> findAllActive(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND p.quantity > 0 AND p.category = :category")
    Page<Product> findAllActiveByCategory(Product.Category category, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND p.quantity > 0 AND (p.name LIKE %:search% OR p.description LIKE %:search%)")
    Page<Product> findAllActiveBySearch(String search, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'active' AND p.quantity > 0 AND p.category = :category AND (p.name LIKE %:search% OR p.description LIKE %:search%)")
    Page<Product> findAllActiveBySearchAndCategory(String search, Product.Category category, Pageable pageable);

    long countByFarmerId(Integer farmerId);
    long countByFarmerIdAndStatus(Integer farmerId, Product.ProductStatus status);

    List<Product> findByFarmerIdOrderByQuantityAsc(Integer farmerId);
}
