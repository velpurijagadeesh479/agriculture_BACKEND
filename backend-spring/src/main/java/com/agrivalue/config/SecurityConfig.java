package com.agrivalue.config;

import com.agrivalue.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/verify", "/api/auth/resend-code").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers("/api/contact").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Farmer endpoints
                .requestMatchers("/api/farmer/**").hasRole("FARMER")
                .requestMatchers(HttpMethod.POST, "/api/products").hasRole("FARMER")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasAnyRole("FARMER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAnyRole("FARMER", "ADMIN")
                .requestMatchers("/api/products/farmer/**").hasRole("FARMER")
                // Buyer endpoints
                .requestMatchers("/api/cart/**").hasRole("BUYER")
                .requestMatchers("/api/wishlist/**").hasRole("BUYER")
                .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("BUYER")
                .requestMatchers("/api/orders/buyer").hasRole("BUYER")
                .requestMatchers("/api/orders/farmer").hasRole("FARMER")
                .requestMatchers("/api/orders/all").hasRole("ADMIN")
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
