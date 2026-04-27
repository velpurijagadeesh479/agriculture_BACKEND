package com.agrivalue.controller;

import com.agrivalue.entity.*;
import com.agrivalue.repository.*;
import com.agrivalue.security.JwtUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final CartItemRepository cartRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final ProductImageRepository imageRepo;

    public OrderController(OrderRepository orderRepo, OrderItemRepository orderItemRepo,
                           CartItemRepository cartRepo, ProductRepository productRepo,
                           UserRepository userRepo, ProductImageRepository imageRepo) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.cartRepo = cartRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.imageRepo = imageRepo;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> placeOrder(@AuthenticationPrincipal JwtUserPrincipal principal,
                                        @RequestBody Map<String, String> body) {
        List<CartItem> cartItems = cartRepo.findByBuyerIdOrderByCreatedAtDesc(principal.getId());
        if (cartItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cart is empty."));
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Product> products = new ArrayList<>();

        for (CartItem ci : cartItems) {
            Product p = productRepo.findById(ci.getProductId()).orElse(null);
            if (p == null) continue;
            if (ci.getQuantity().compareTo(p.getQuantity()) > 0) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Insufficient stock for " + p.getName() + ". Available: " + p.getQuantity()));
            }
            subtotal = subtotal.add(p.getPrice().multiply(ci.getQuantity()));
            products.add(p);
        }

        BigDecimal shipping = new BigDecimal("50");
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = subtotal.add(shipping).add(tax);

        Order order = Order.builder()
                .buyerId(principal.getId())
                .totalAmount(grandTotal)
                .shippingAddress(body.getOrDefault("shippingAddress", "Default Address"))
                .status(Order.OrderStatus.pending)
                .paymentStatus(Order.PaymentStatus.paid)
                .build();
        order = orderRepo.save(order);

        for (int i = 0; i < cartItems.size(); i++) {
            CartItem ci = cartItems.get(i);
            Product p = products.get(i);

            orderItemRepo.save(OrderItem.builder()
                    .orderId(order.getId()).productId(ci.getProductId())
                    .farmerId(p.getFarmerId()).quantity(ci.getQuantity())
                    .priceAtPurchase(p.getPrice()).unit(p.getUnit()).build());

            p.setQuantity(p.getQuantity().subtract(ci.getQuantity()));
            if (p.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                p.setStatus(Product.ProductStatus.out_of_stock);
            }
            productRepo.save(p);
        }

        cartRepo.deleteByBuyerId(principal.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Order placed successfully!",
                "orderId", order.getId(),
                "total", grandTotal.setScale(2, RoundingMode.HALF_UP).toString()));
    }

    @GetMapping("/buyer")
    public ResponseEntity<?> getBuyerOrders(@AuthenticationPrincipal JwtUserPrincipal principal) {
        List<Order> orders = orderRepo.findByBuyerIdOrderByCreatedAtDesc(principal.getId());
        List<Map<String, Object>> result = orders.stream().map(this::enrichOrder).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("orders", result));
    }

    @GetMapping("/farmer")
    public ResponseEntity<?> getFarmerOrders(@AuthenticationPrincipal JwtUserPrincipal principal) {
        List<OrderItem> items = orderItemRepo.findByFarmerIdOrderByCreatedAtDesc(principal.getId());
        List<Map<String, Object>> result = items.stream().map(oi -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", oi.getId());
            map.put("order_id", oi.getOrderId());
            map.put("quantity", oi.getQuantity());
            map.put("price_at_purchase", oi.getPriceAtPurchase());
            map.put("unit", oi.getUnit());
            map.put("status", oi.getStatus().name());
            productRepo.findById(oi.getProductId()).ifPresent(p -> {
                map.put("product_name", p.getName());
                map.put("category", p.getCategory().name());
            });
            orderRepo.findById(oi.getOrderId()).ifPresent(o -> {
                map.put("order_status", o.getStatus().name());
                map.put("order_date", o.getCreatedAt());
                map.put("payment_status", o.getPaymentStatus().name());
                userRepo.findById(o.getBuyerId()).ifPresent(u -> {
                    map.put("buyer_name", u.getName());
                    map.put("buyer_email", u.getEmail());
                    map.put("buyer_phone", u.getPhone());
                });
            });
            imageRepo.findFirstByProductIdAndIsPrimaryTrue(oi.getProductId())
                    .ifPresent(img -> map.put("image", img.getImageUrl()));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("orders", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable Integer id) {
        Optional<Order> opt = orderRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("order", enrichOrder(opt.get())));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateOrderStatus(@AuthenticationPrincipal JwtUserPrincipal principal,
                                               @PathVariable Integer id, @RequestBody Map<String, String> body) {
        Optional<Order> opt = orderRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Order order = opt.get();
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(body.get("status"));
        order.setStatus(newStatus);
        orderRepo.save(order);

        if (principal.getRole().equals("admin")) {
            List<OrderItem> items = orderItemRepo.findByOrderId(id);
            items.forEach(oi -> { oi.setStatus(newStatus); orderItemRepo.save(oi); });
        } else {
            List<OrderItem> items = orderItemRepo.findByOrderIdAndFarmerId(id, principal.getId());
            items.forEach(oi -> { oi.setStatus(newStatus); orderItemRepo.save(oi); });
        }

        return ResponseEntity.ok(Map.of("message", "Order status updated."));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllOrders() {
        List<Order> orders = orderRepo.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = orders.stream().map(o -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", o.getId());
            map.put("total_amount", o.getTotalAmount());
            map.put("status", o.getStatus().name());
            map.put("payment_status", o.getPaymentStatus().name());
            map.put("created_at", o.getCreatedAt());
            map.put("item_count", orderItemRepo.findByOrderId(o.getId()).size());
            userRepo.findById(o.getBuyerId()).ifPresent(u -> {
                map.put("buyer_name", u.getName());
                map.put("buyer_email", u.getEmail());
            });
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("orders", result));
    }

    private Map<String, Object> enrichOrder(Order o) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", o.getId());
        map.put("buyer_id", o.getBuyerId());
        map.put("total_amount", o.getTotalAmount());
        map.put("status", o.getStatus().name());
        map.put("payment_status", o.getPaymentStatus().name());
        map.put("created_at", o.getCreatedAt());
        map.put("shipping_address", o.getShippingAddress());

        List<Map<String, Object>> items = orderItemRepo.findByOrderId(o.getId()).stream().map(oi -> {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("id", oi.getId());
            itemMap.put("product_id", oi.getProductId());
            itemMap.put("quantity", oi.getQuantity());
            itemMap.put("price_at_purchase", oi.getPriceAtPurchase());
            itemMap.put("unit", oi.getUnit());
            itemMap.put("status", oi.getStatus().name());
            productRepo.findById(oi.getProductId()).ifPresent(p -> {
                itemMap.put("product_name", p.getName());
                itemMap.put("category", p.getCategory().name());
            });
            userRepo.findById(oi.getFarmerId()).ifPresent(u -> itemMap.put("farmer_name", u.getName()));
            imageRepo.findFirstByProductIdAndIsPrimaryTrue(oi.getProductId())
                    .ifPresent(img -> itemMap.put("image", img.getImageUrl()));
            return itemMap;
        }).collect(Collectors.toList());

        map.put("items", items);
        map.put("item_count", items.size());
        userRepo.findById(o.getBuyerId()).ifPresent(u -> {
            map.put("buyer_name", u.getName());
            map.put("buyer_email", u.getEmail());
        });
        return map;
    }
}
