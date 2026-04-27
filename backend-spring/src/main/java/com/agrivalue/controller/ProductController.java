package com.agrivalue.controller;

import com.agrivalue.entity.Product;
import com.agrivalue.entity.ProductImage;
import com.agrivalue.entity.User;
import com.agrivalue.repository.ProductImageRepository;
import com.agrivalue.repository.ProductRepository;
import com.agrivalue.repository.UserRepository;
import com.agrivalue.security.JwtUserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepo;
    private final ProductImageRepository imageRepo;
    private final UserRepository userRepo;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public ProductController(ProductRepository productRepo, ProductImageRepository imageRepo, UserRepository userRepo) {
        this.productRepo = productRepo;
        this.imageRepo = imageRepo;
        this.userRepo = userRepo;
    }

    private String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(uploadPath);
        String filename = System.currentTimeMillis() + "-" + new Random().nextInt(1000000000) + getExtension(file.getOriginalFilename());
        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath.toFile());
        return "/uploads/" + filename;
    }

    private String getExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : ".jpg";
    }

    private Map<String, Object> toProductMap(Product p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("farmer_id", p.getFarmerId());
        map.put("name", p.getName());
        map.put("category", p.getCategory().name());
        map.put("description", p.getDescription());
        map.put("price", p.getPrice());
        map.put("quantity", p.getQuantity());
        map.put("unit", p.getUnit());
        map.put("location", p.getLocation());
        map.put("status", p.getStatus().name());
        map.put("created_at", p.getCreatedAt());

        List<String> images = p.getImages() != null ?
                p.getImages().stream().map(ProductImage::getImageUrl).collect(Collectors.toList()) : List.of();
        map.put("images", images);

        String primaryImage = p.getImages() != null ?
                p.getImages().stream().filter(ProductImage::getIsPrimary).map(ProductImage::getImageUrl).findFirst()
                        .orElse(images.isEmpty() ? null : images.get(0)) : null;
        map.put("primary_image", primaryImage);

        if (p.getFarmerName() != null) map.put("farmer_name", p.getFarmerName());
        if (p.getFarmerLocation() != null) map.put("farmer_location", p.getFarmerLocation());

        return map;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> addProduct(@AuthenticationPrincipal JwtUserPrincipal principal,
                                        @RequestParam String name, @RequestParam String category,
                                        @RequestParam(required = false) String description,
                                        @RequestParam BigDecimal price, @RequestParam BigDecimal quantity,
                                        @RequestParam(defaultValue = "kg") String unit,
                                        @RequestParam(required = false) String location,
                                        @RequestParam(required = false) List<MultipartFile> images) throws IOException {

        Product product = Product.builder()
                .farmerId(principal.getId()).name(name)
                .category(Product.Category.valueOf(category))
                .description(description).price(price).quantity(quantity)
                .unit(unit).location(location).build();
        product = productRepo.save(product);

        if (images != null) {
            for (int i = 0; i < images.size(); i++) {
                String imageUrl = saveFile(images.get(i));
                imageRepo.save(ProductImage.builder()
                        .productId(product.getId()).imageUrl(imageUrl).isPrimary(i == 0).build());
            }
        }

        Product saved = productRepo.findById(product.getId()).orElse(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Product added successfully!", "product", toProductMap(saved)));
    }

    @GetMapping
    public ResponseEntity<?> getAllProducts(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "12") int limit,
                                            @RequestParam(defaultValue = "newest") String sort) {
        Sort sortOrder = switch (sort) {
            case "price_low" -> Sort.by("price").ascending();
            case "price_high" -> Sort.by("price").descending();
            case "oldest" -> Sort.by("createdAt").ascending();
            default -> Sort.by("createdAt").descending();
        };

        PageRequest pageable = PageRequest.of(page - 1, limit, sortOrder);
        Page<Product> products;

        boolean hasSearch = search != null && !search.isEmpty();
        boolean hasCategory = category != null && !category.isEmpty() && !category.equals("all");

        if (hasSearch && hasCategory) {
            products = productRepo.findAllActiveBySearchAndCategory(search, Product.Category.valueOf(category), pageable);
        } else if (hasSearch) {
            products = productRepo.findAllActiveBySearch(search, pageable);
        } else if (hasCategory) {
            products = productRepo.findAllActiveByCategory(Product.Category.valueOf(category), pageable);
        } else {
            products = productRepo.findAllActive(pageable);
        }

        // Enrich with farmer info
        List<Map<String, Object>> result = products.getContent().stream().map(p -> {
            userRepo.findById(p.getFarmerId()).ifPresent(u -> {
                p.setFarmerName(u.getName());
                p.setFarmerLocation(u.getLocation());
            });
            return toProductMap(p);
        }).collect(Collectors.toList());

        Map<String, Object> pagination = Map.of(
                "total", products.getTotalElements(), "page", page,
                "limit", limit, "totalPages", products.getTotalPages());

        return ResponseEntity.ok(Map.of("products", result, "pagination", pagination));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Integer id) {
        Optional<Product> opt = productRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Product p = opt.get();
        userRepo.findById(p.getFarmerId()).ifPresent(u -> {
            p.setFarmerName(u.getName());
            p.setFarmerLocation(u.getLocation());
        });

        Map<String, Object> data = toProductMap(p);
        userRepo.findById(p.getFarmerId()).ifPresent(u -> {
            data.put("farmer_name", u.getName());
            data.put("farmer_email", u.getEmail());
            data.put("farmer_phone", u.getPhone());
            data.put("farmer_location", u.getLocation());
            data.put("farm_name", u.getBusinessName());
        });

        return ResponseEntity.ok(data);
    }

    @GetMapping("/farmer/mine")
    public ResponseEntity<?> getMyProducts(@AuthenticationPrincipal JwtUserPrincipal principal) {
        List<Product> products = productRepo.findByFarmerIdOrderByCreatedAtDesc(principal.getId());
        List<Map<String, Object>> result = products.stream().map(this::toProductMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("products", result));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateProduct(@AuthenticationPrincipal JwtUserPrincipal principal,
                                           @PathVariable Integer id,
                                           @RequestParam String name, @RequestParam String category,
                                           @RequestParam(required = false) String description,
                                           @RequestParam BigDecimal price, @RequestParam BigDecimal quantity,
                                           @RequestParam(defaultValue = "kg") String unit,
                                           @RequestParam(required = false) String location,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) List<MultipartFile> images) throws IOException {

        Optional<Product> opt = productRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Product product = opt.get();
        if (!product.getFarmerId().equals(principal.getId()) && !principal.getRole().equals("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized."));
        }

        product.setName(name);
        product.setCategory(Product.Category.valueOf(category));
        product.setDescription(description);
        product.setPrice(price);
        product.setQuantity(quantity);
        product.setUnit(unit);
        product.setLocation(location);
        if (status != null) product.setStatus(Product.ProductStatus.valueOf(status));
        productRepo.save(product);

        if (images != null) {
            for (MultipartFile img : images) {
                String imageUrl = saveFile(img);
                imageRepo.save(ProductImage.builder().productId(id).imageUrl(imageUrl).isPrimary(false).build());
            }
        }

        return ResponseEntity.ok(Map.of("message", "Product updated successfully."));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteProduct(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable Integer id) {
        Optional<Product> opt = productRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Product product = opt.get();
        if (!product.getFarmerId().equals(principal.getId()) && !principal.getRole().equals("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not authorized."));
        }

        // Delete image files
        List<ProductImage> images = imageRepo.findByProductId(id);
        for (ProductImage img : images) {
            Path filePath = Paths.get(uploadDir).toAbsolutePath().resolve(img.getImageUrl().replace("/uploads/", ""));
            try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
        }

        productRepo.delete(product);
        return ResponseEntity.ok(Map.of("message", "Product deleted successfully."));
    }
}
