package com.agrivalue.repository;

import com.agrivalue.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndRole(String email, User.Role role);
    boolean existsByEmail(String email);
    List<User> findByRole(User.Role role);

    @Query("SELECT u FROM User u WHERE u.role = :role AND (u.name LIKE %:search% OR u.email LIKE %:search%)")
    List<User> findByRoleAndSearch(User.Role role, String search);

    long countByRole(User.Role role);
    long countByRoleAndStatus(User.Role role, User.Status status);
}
