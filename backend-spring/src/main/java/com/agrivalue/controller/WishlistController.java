package com.agrivalue.controller;

import com.agrivalue.entity.WishlistItem;
import com.agrivalue.repository.ProductImageRepository;
import com.agrivalue.repository.ProductRepository;
import com.agrivalue.repository.UserRepository;
import com.agrivalue.repository.WishlistItemRepository;
import com.agrivalue.security.JwtUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistItemRepository wishlistRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final ProductImageRepository imageRepo;

    public WishlistController(WishlistItemRepository wishlistRepo, ProductRepository productRepo,
                              UserRepository userRepo, ProductImageRepository imageRepo) {
        this.wishlistRepo = wishlistRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.imageRepo = imageRepo;
    }

    @GetMapping
    public ResponseEntity<?> getWishlist(@AuthenticationPrincipal JwtUserPrincipal principal) {
        List<WishlistItem> items = wishlistRepo.findByBuyerIdOrderByCreatedAtDesc(principal.getId());
        List<Map<String, Object>> result = items.stream().map(w -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", w.getId());
            map.put("product_id", w.getProductId());
            map.put("created_at", w.getCreatedAt());
            productRepo.findById(w.getProductId()).ifPresent(p -> {
                map.put("name", p.getName());
                map.put("price", p.getPrice());
                map.put("unit", p.getUnit());
                map.put("category", p.getCategory().name());
                map.put("stock", p.getQuantity());
                map.put("status", p.getStatus().name());
                userRepo.findById(p.getFarmerId()).ifPresent(u -> map.put("farmer_name", u.getName()));
            });
            imageRepo.findFirstByProductIdAndIsPrimaryTrue(w.getProductId())
                    .ifPresent(img -> map.put("image", img.getImageUrl()));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("items", result));
    }

    @PostMapping
    public ResponseEntity<?> addToWishlist(@AuthenticationPrincipal JwtUserPrincipal principal,
                                           @RequestBody Map<String, Object> body) {
        Integer productId = Integer.valueOf(body.get("productId").toString());
        if (productRepo.findById(productId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (wishlistRepo.findByBuyerIdAndProductId(principal.getId(), productId).isEmpty()) {
            wishlistRepo.save(WishlistItem.builder().buyerId(principal.getId()).productId(productId).build());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Added to wishlist."));
    }

    @DeleteMapping("/{productId}")
    @Transactional
    public ResponseEntity<?> removeFromWishlist(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @PathVariable Integer productId) {
        wishlistRepo.deleteByBuyerIdAndProductId(principal.getId(), productId);
        return ResponseEntity.ok(Map.of("message", "Removed from wishlist."));
    }
}
