package com.agrivalue.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "farmer_id", nullable = false)
    private Integer farmerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "price_at_purchase", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(length = 20)
    private String unit;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Order.OrderStatus status = Order.OrderStatus.pending;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String productName;

    @Transient
    private String farmerName;

    @Transient
    private String buyerName;

    @Transient
    private String image;

    @Transient
    private String category;
}
