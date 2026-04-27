package com.agrivalue.controller;

import com.agrivalue.entity.Order;
import com.agrivalue.entity.Product;
import com.agrivalue.repository.*;
import com.agrivalue.security.JwtUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/farmer")
public class FarmerController {

    private final ProductRepository productRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderRepository orderRepo;
    private final UserRepository userRepo;
    private final ProductImageRepository imageRepo;

    public FarmerController(ProductRepository productRepo, OrderItemRepository orderItemRepo,
                            OrderRepository orderRepo, UserRepository userRepo, ProductImageRepository imageRepo) {
        this.productRepo = productRepo;
        this.orderItemRepo = orderItemRepo;
        this.orderRepo = orderRepo;
        this.userRepo = userRepo;
        this.imageRepo = imageRepo;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats(@AuthenticationPrincipal JwtUserPrincipal principal) {
        Integer farmerId = principal.getId();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProducts", productRepo.countByFarmerId(farmerId));
        stats.put("activeProducts", productRepo.countByFarmerIdAndStatus(farmerId, Product.ProductStatus.active));
        stats.put("totalOrders", orderItemRepo.countByFarmerId(farmerId));
        stats.put("pendingOrders", orderItemRepo.countByFarmerIdAndStatus(farmerId, Order.OrderStatus.pending));
        stats.put("totalEarnings", orderItemRepo.getTotalEarningsByFarmerId(farmerId));

        List<Map<String, Object>> recentOrders = orderItemRepo.findByFarmerIdOrderByCreatedAtDesc(farmerId)
                .stream().limit(5).map(oi -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("price_at_purchase", oi.getPriceAtPurchase());
                    m.put("quantity", oi.getQuantity());
                    productRepo.findById(oi.getProductId()).ifPresent(p -> m.put("product_name", p.getName()));
                    orderRepo.findById(oi.getOrderId()).ifPresent(o -> {
                        m.put("order_date", o.getCreatedAt());
                        m.put("order_status", o.getStatus().name());
                        userRepo.findById(o.getBuyerId()).ifPresent(u -> m.put("buyer_name", u.getName()));
                    });
                    return m;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("stats", stats, "recentOrders", recentOrders));
    }

    @GetMapping("/inventory")
    public ResponseEntity<?> getInventory(@AuthenticationPrincipal JwtUserPrincipal principal) {
        List<Product> products = productRepo.findByFarmerIdOrderByQuantityAsc(principal.getId());

        int totalItems = products.size();
        int lowStock = (int) products.stream().filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) > 0 && p.getQuantity().compareTo(new BigDecimal("10")) <= 0).count();
        int outOfStock = (int) products.stream().filter(p -> p.getQuantity().compareTo(BigDecimal.ZERO) <= 0).count();

        List<Map<String, Object>> result = products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId()); m.put("name", p.getName()); m.put("category", p.getCategory().name());
            m.put("quantity", p.getQuantity()); m.put("unit", p.getUnit()); m.put("price", p.getPrice());
            m.put("status", p.getStatus().name());
            imageRepo.findFirstByProductIdAndIsPrimaryTrue(p.getId()).ifPresent(img -> m.put("image", img.getImageUrl()));
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("stats", Map.of("totalItems", totalItems, "lowStock", lowStock, "outOfStock", outOfStock), "products", result));
    }

    @GetMapping("/earnings")
    public ResponseEntity<?> getEarnings(@AuthenticationPrincipal JwtUserPrincipal principal) {
        Integer farmerId = principal.getId();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEarnings", orderItemRepo.getTotalEarningsByFarmerId(farmerId));
        stats.put("thisMonth", orderItemRepo.getTotalEarningsByFarmerId(farmerId)); // simplified
        stats.put("pending", orderItemRepo.getPendingEarningsByFarmerId(farmerId));

        List<Map<String, Object>> transactions = orderItemRepo.findByFarmerIdOrderByCreatedAtDesc(farmerId)
                .stream().limit(10).map(oi -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("price_at_purchase", oi.getPriceAtPurchase());
                    m.put("quantity", oi.getQuantity());
                    m.put("amount", oi.getPriceAtPurchase().multiply(oi.getQuantity()));
                    productRepo.findById(oi.getProductId()).ifPresent(p -> m.put("product_name", p.getName()));
                    orderRepo.findById(oi.getOrderId()).ifPresent(o -> {
                        m.put("order_date", o.getCreatedAt());
                        m.put("payment_status", o.getPaymentStatus().name());
                        userRepo.findById(o.getBuyerId()).ifPresent(u -> m.put("buyer_name", u.getName()));
                    });
                    return m;
                }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("stats", stats, "transactions", transactions, "monthlyEarnings", List.of()));
    }
}
