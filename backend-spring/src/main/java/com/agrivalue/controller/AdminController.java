package com.agrivalue.controller;

import com.agrivalue.entity.Order;
import com.agrivalue.entity.User;
import com.agrivalue.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;

    public AdminController(UserRepository userRepo, ProductRepository productRepo,
                           OrderRepository orderRepo, OrderItemRepository orderItemRepo) {
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepo.count());
        stats.put("activeFarmers", userRepo.countByRoleAndStatus(User.Role.farmer, User.Status.active));
        stats.put("activeBuyers", userRepo.countByRoleAndStatus(User.Role.buyer, User.Status.active));
        stats.put("totalProducts", productRepo.count());
        stats.put("totalOrders", orderRepo.count());
        stats.put("revenue", orderRepo.getTotalRevenue());

        List<Map<String, Object>> recentUsers = userRepo.findAll(
                org.springframework.data.domain.PageRequest.of(0, 5,
                        org.springframework.data.domain.Sort.by("createdAt").descending())
        ).getContent().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId()); m.put("name", u.getName()); m.put("email", u.getEmail());
            m.put("role", u.getRole().name()); m.put("status", u.getStatus().name()); m.put("created_at", u.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> recentOrders = orderRepo.findAllByOrderByCreatedAtDesc().stream().limit(5).map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId()); m.put("total_amount", o.getTotalAmount());
            m.put("status", o.getStatus().name()); m.put("created_at", o.getCreatedAt());
            userRepo.findById(o.getBuyerId()).ifPresent(u -> m.put("buyer_name", u.getName()));
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("stats", stats, "recentUsers", recentUsers, "recentOrders", recentOrders));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestParam(required = false) String search,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) String status) {
        List<User> users = userRepo.findAll();
        if (search != null && !search.isEmpty()) {
            String s = search.toLowerCase();
            users = users.stream().filter(u -> u.getName().toLowerCase().contains(s) || u.getEmail().toLowerCase().contains(s)).collect(Collectors.toList());
        }
        if (role != null && !role.isEmpty()) {
            users = users.stream().filter(u -> u.getRole().name().equals(role)).collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            users = users.stream().filter(u -> u.getStatus().name().equals(status)).collect(Collectors.toList());
        }

        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId()); m.put("name", u.getName()); m.put("email", u.getEmail());
            m.put("role", u.getRole().name()); m.put("phone", u.getPhone()); m.put("location", u.getLocation());
            m.put("business_name", u.getBusinessName()); m.put("status", u.getStatus().name());
            m.put("is_verified", u.getIsVerified()); m.put("created_at", u.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("users", result));
    }

    @GetMapping("/farmers")
    public ResponseEntity<?> getAllFarmers(@RequestParam(required = false, defaultValue = "") String search) {
        List<User> farmers = search.isEmpty() ? userRepo.findByRole(User.Role.farmer) :
                userRepo.findByRoleAndSearch(User.Role.farmer, search);

        List<Map<String, Object>> result = farmers.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId()); m.put("name", f.getName()); m.put("email", f.getEmail());
            m.put("phone", f.getPhone()); m.put("location", f.getLocation());
            m.put("business_name", f.getBusinessName()); m.put("status", f.getStatus().name());
            m.put("created_at", f.getCreatedAt());
            m.put("product_count", productRepo.countByFarmerId(f.getId()));
            m.put("order_count", orderItemRepo.countByFarmerId(f.getId()));
            m.put("total_earnings", orderItemRepo.getTotalEarningsByFarmerId(f.getId()));
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("farmers", result));
    }

    @GetMapping("/buyers")
    public ResponseEntity<?> getAllBuyers(@RequestParam(required = false, defaultValue = "") String search) {
        List<User> buyers = search.isEmpty() ? userRepo.findByRole(User.Role.buyer) :
                userRepo.findByRoleAndSearch(User.Role.buyer, search);

        List<Map<String, Object>> result = buyers.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId()); m.put("name", b.getName()); m.put("email", b.getEmail());
            m.put("phone", b.getPhone()); m.put("location", b.getLocation());
            m.put("business_name", b.getBusinessName()); m.put("status", b.getStatus().name());
            m.put("created_at", b.getCreatedAt());
            long orderCount = orderRepo.findByBuyerIdOrderByCreatedAtDesc(b.getId()).size();
            BigDecimal totalSpent = orderRepo.findByBuyerIdOrderByCreatedAtDesc(b.getId()).stream()
                    .filter(o -> o.getPaymentStatus() == Order.PaymentStatus.paid)
                    .map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            m.put("order_count", orderCount);
            m.put("total_spent", totalSpent);
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("buyers", result));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) String status) {
        List<Order> orders = orderRepo.findAllByOrderByCreatedAtDesc();
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("All Status")) {
            orders = orders.stream().filter(o -> o.getStatus().name().equalsIgnoreCase(status)).collect(Collectors.toList());
        }
        if (search != null && !search.isEmpty()) {
            String s = search.toLowerCase();
            orders = orders.stream().filter(o -> {
                Optional<User> u = userRepo.findById(o.getBuyerId());
                return o.getId().toString().contains(s) ||
                        (u.isPresent() && (u.get().getName().toLowerCase().contains(s) || u.get().getEmail().toLowerCase().contains(s)));
            }).collect(Collectors.toList());
        }

        List<Map<String, Object>> result = orders.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId()); m.put("total_amount", o.getTotalAmount());
            m.put("status", o.getStatus().name()); m.put("payment_status", o.getPaymentStatus().name());
            m.put("created_at", o.getCreatedAt());
            m.put("item_count", orderItemRepo.findByOrderId(o.getId()).size());
            userRepo.findById(o.getBuyerId()).ifPresent(u -> { m.put("buyer_name", u.getName()); m.put("buyer_email", u.getEmail()); });
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("transactions", result));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        userRepo.findById(id).ifPresent(u -> {
            u.setStatus(User.Status.valueOf(body.get("status")));
            userRepo.save(u);
        });
        return ResponseEntity.ok(Map.of("message", "User status updated."));
    }
}
