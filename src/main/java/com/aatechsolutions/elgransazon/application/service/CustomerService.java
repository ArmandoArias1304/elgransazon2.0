package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Customer;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Customer management
 */
public interface CustomerService {
    
    /**
     * Find all customers
     */
    List<Customer> findAll();
    
    /**
     * Find all active customers
     */
    List<Customer> findAllActive();
    
    /**
     * Find customer by ID
     */
    Optional<Customer> findById(Long id);
    
    /**
     * Find customer by email
     */
    Optional<Customer> findByEmail(String email);
    
    /**
     * Find customer by username
     */
    Optional<Customer> findByUsername(String username);
    
    /**
     * Find customer by username or email
     */
    Optional<Customer> findByUsernameOrEmail(String usernameOrEmail);
    
    /**
     * Create new customer
     */
    Customer create(Customer customer);
    
    /**
     * Update existing customer
     */
    Customer update(Long id, Customer customer);
    
    /**
     * Delete customer
     */
    void delete(Long id);
    
    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Check if phone exists
     */
    boolean existsByPhone(String phone);
    
    /**
     * Update last access timestamp
     */
    void updateLastAccess(String email);
    
    /**
     * Activate customer
     */
    Customer activate(Long id);
    
    /**
     * Deactivate customer
     */
    Customer deactivate(Long id);
}
