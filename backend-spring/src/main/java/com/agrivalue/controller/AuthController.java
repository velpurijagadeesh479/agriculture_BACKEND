package com.agrivalue.controller;

import com.agrivalue.entity.User;
import com.agrivalue.entity.VerificationCode;
import com.agrivalue.repository.UserRepository;
import com.agrivalue.repository.VerificationCodeRepository;
import com.agrivalue.security.JwtUserPrincipal;
import com.agrivalue.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final VerificationCodeRepository codeRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepo, VerificationCodeRepository codeRepo,
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.codeRepo = codeRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    private String generateCode() {
        return String.valueOf(100000 + new Random().nextInt(900000));
    }

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (userRepo.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "User with this email already exists."));
        }

        User user = User.builder()
                .name(body.get("name"))
                .email(email)
                .password(passwordEncoder.encode(body.get("password")))
                .phone(body.get("phone"))
                .role(User.Role.valueOf(body.get("role")))
                .location(body.get("location"))
                .businessName(body.get("businessName"))
                .build();
        user = userRepo.save(user);

        String code = generateCode();
        codeRepo.deleteByEmail(email);
        codeRepo.save(VerificationCode.builder()
                .email(email).code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Account created successfully. Please verify your account.",
                "userId", user.getId(),
                "verificationCode", code,
                "email", email,
                "role", body.get("role")
        ));
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String role = body.get("role");

        Optional<User> optUser = userRepo.findByEmailAndRole(email, User.Role.valueOf(role));
        if (optUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email, role, or password."));
        }

        User user = optUser.get();
        if (user.getStatus() != User.Status.active) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Your account has been suspended or deactivated."));
        }
        if (!passwordEncoder.matches(body.get("password"), user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email, role, or password."));
        }

        String code = generateCode();
        codeRepo.deleteByEmail(email);
        codeRepo.save(VerificationCode.builder()
                .email(email).code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());

        return ResponseEntity.ok(Map.of(
                "message", "Verification code sent.",
                "verificationCode", code,
                "email", email,
                "role", role,
                "userName", user.getName()
        ));
    }

    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");

        Optional<VerificationCode> optCode = codeRepo
                .findFirstByEmailAndCodeAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, code, LocalDateTime.now());
        if (optCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired verification code."));
        }

        VerificationCode vc = optCode.get();
        vc.setUsed(true);
        codeRepo.save(vc);

        Optional<User> optUser = userRepo.findByEmail(email);
        if (optUser.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found."));
        }

        User user = optUser.get();
        user.setIsVerified(true);
        userRepo.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name(), user.getName());

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("email", user.getEmail());
        userData.put("role", user.getRole().name());
        userData.put("phone", user.getPhone());
        userData.put("location", user.getLocation());
        userData.put("businessName", user.getBusinessName());
        userData.put("bio", user.getBio());
        userData.put("profilePhoto", user.getProfilePhoto());
        userData.put("status", user.getStatus().name());

        return ResponseEntity.ok(Map.of("message", "Verification successful!", "token", token, "user", userData));
    }

    @PostMapping("/resend-code")
    @Transactional
    public ResponseEntity<?> resendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (!userRepo.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found."));
        }

        String code = generateCode();
        codeRepo.deleteByEmail(email);
        codeRepo.save(VerificationCode.builder()
                .email(email).code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());

        return ResponseEntity.ok(Map.of("message", "New verification code generated.", "verificationCode", code));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal JwtUserPrincipal principal) {
        Optional<User> optUser = userRepo.findById(principal.getId());
        if (optUser.isEmpty()) return ResponseEntity.notFound().build();

        User user = optUser.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        data.put("role", user.getRole().name());
        data.put("phone", user.getPhone());
        data.put("location", user.getLocation());
        data.put("businessName", user.getBusinessName());
        data.put("bio", user.getBio());
        data.put("profilePhoto", user.getProfilePhoto());
        data.put("status", user.getStatus().name());
        data.put("createdAt", user.getCreatedAt());
        return ResponseEntity.ok(data);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal JwtUserPrincipal principal,
                                           @RequestBody Map<String, String> body) {
        Optional<User> optUser = userRepo.findById(principal.getId());
        if (optUser.isEmpty()) return ResponseEntity.notFound().build();

        User user = optUser.get();
        user.setName(body.getOrDefault("name", user.getName()));
        user.setPhone(body.get("phone"));
        user.setLocation(body.get("location"));
        user.setBusinessName(body.get("businessName"));
        user.setBio(body.get("bio"));
        userRepo.save(user);

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("email", user.getEmail());
        userData.put("role", user.getRole().name());
        userData.put("phone", user.getPhone());
        userData.put("location", user.getLocation());
        userData.put("businessName", user.getBusinessName());
        userData.put("bio", user.getBio());
        userData.put("profilePhoto", user.getProfilePhoto());
        userData.put("status", user.getStatus().name());

        return ResponseEntity.ok(Map.of("message", "Profile updated successfully.", "user", userData));
    }
}
