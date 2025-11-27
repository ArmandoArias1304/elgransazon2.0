package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Customer entity
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    /**
     * Find customer by username (case insensitive)
     */
    Optional<Customer> findByUsernameIgnoreCase(String username);
    
    /**
     * Find customer by email (case insensitive)
     */
    Optional<Customer> findByEmailIgnoreCase(String email);
    
    /**
     * Find customer by username OR email (case insensitive)
     */
    Optional<Customer> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    
    /**
     * Check if username exists (case insensitive)
     */
    boolean existsByUsernameIgnoreCase(String username);
    
    /**
     * Check if email exists (case insensitive)
     */
    boolean existsByEmailIgnoreCase(String email);
    
    /**
     * Check if phone exists
     */
    boolean existsByPhone(String phone);
    
    /**
     * Find all active customers
     */
    java.util.List<Customer> findByActiveTrue();
}
