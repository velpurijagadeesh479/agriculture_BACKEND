package com.agrivalue.controller;

import com.agrivalue.entity.CartItem;
import com.agrivalue.entity.Product;
import com.agrivalue.repository.CartItemRepository;
import com.agrivalue.repository.ProductRepository;
import com.agrivalue.repository.ProductImageRepository;
import com.agrivalue.repository.UserRepository;
import com.agrivalue.security.JwtUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemRepository cartRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;
    private final ProductImageRepository imageRepo;

    public CartController(CartItemRepository cartRepo, ProductRepository productRepo,
                          UserRepository userRepo, ProductImageRepository imageRepo) {
        this.cartRepo = cartRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
        this.imageRepo = imageRepo;
    }

    @GetMapping
    public ResponseEntity<?> getCart(@AuthenticationPrincipal JwtUserPrincipal principal) {
        List<CartItem> cartItems = cartRepo.findByBuyerIdOrderByCreatedAtDesc(principal.getId());

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            productRepo.findById(ci.getProductId()).ifPresent(p -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", ci.getId());
                item.put("quantity", ci.getQuantity());
                item.put("product_id", ci.getProductId());
                item.put("name", p.getName());
                item.put("price", p.getPrice());
                item.put("unit", p.getUnit());
                item.put("stock", p.getQuantity());
                item.put("category", p.getCategory().name());
                userRepo.findById(p.getFarmerId()).ifPresent(u -> item.put("farmer_name", u.getName()));
                imageRepo.findFirstByProductIdAndIsPrimaryTrue(p.getId())
                        .ifPresent(img -> item.put("image", img.getImageUrl()));
                items.add(item);
            });
        }

        for (var item : items) {
            BigDecimal price = (BigDecimal) item.get("price");
            BigDecimal qty = cartItems.stream()
                    .filter(ci -> ci.getId().equals(item.get("id")))
                    .findFirst().map(CartItem::getQuantity).orElse(BigDecimal.ZERO);
            subtotal = subtotal.add(price.multiply(qty));
        }

        BigDecimal shipping = subtotal.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("50") : BigDecimal.ZERO;
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(shipping).add(tax);

        Map<String, Object> summary = Map.of(
                "subtotal", subtotal.setScale(2, RoundingMode.HALF_UP).toString(),
                "shipping", shipping.setScale(2, RoundingMode.HALF_UP).toString(),
                "tax", tax.toString(),
                "total", total.setScale(2, RoundingMode.HALF_UP).toString(),
                "itemCount", items.size());

        return ResponseEntity.ok(Map.of("items", items, "summary", summary));
    }

    @PostMapping
    public ResponseEntity<?> addToCart(@AuthenticationPrincipal JwtUserPrincipal principal,
                                       @RequestBody Map<String, Object> body) {
        Integer productId = Integer.valueOf(body.get("productId").toString());
        BigDecimal quantity = body.containsKey("quantity") ?
                new BigDecimal(body.get("quantity").toString()) : BigDecimal.ONE;

        Optional<Product> optProduct = productRepo.findById(productId);
        if (optProduct.isEmpty() || optProduct.get().getStatus() != Product.ProductStatus.active) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found or unavailable."));
        }

        Product product = optProduct.get();
        if (product.getFarmerId().equals(principal.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot add your own product to cart."));
        }
        if (quantity.compareTo(product.getQuantity()) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Requested quantity exceeds available stock."));
        }

        Optional<CartItem> existing = cartRepo.findByBuyerIdAndProductId(principal.getId(), productId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity().add(quantity));
            cartRepo.save(item);
        } else {
            cartRepo.save(CartItem.builder().buyerId(principal.getId()).productId(productId).quantity(quantity).build());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Item added to cart."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCartItem(@AuthenticationPrincipal JwtUserPrincipal principal,
                                            @PathVariable Integer id, @RequestBody Map<String, Object> body) {
        BigDecimal quantity = new BigDecimal(body.get("quantity").toString());

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            cartRepo.findByIdAndBuyerId(id, principal.getId()).ifPresent(cartRepo::delete);
            return ResponseEntity.ok(Map.of("message", "Item removed from cart."));
        }

        cartRepo.findByIdAndBuyerId(id, principal.getId()).ifPresent(item -> {
            item.setQuantity(quantity);
            cartRepo.save(item);
        });

        return ResponseEntity.ok(Map.of("message", "Cart updated."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeFromCart(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable Integer id) {
        cartRepo.findByIdAndBuyerId(id, principal.getId()).ifPresent(cartRepo::delete);
        return ResponseEntity.ok(Map.of("message", "Item removed from cart."));
    }
}
