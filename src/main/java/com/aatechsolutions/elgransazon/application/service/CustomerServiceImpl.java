package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service implementation for Customer management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<Customer> findAll() {
        log.debug("Finding all customers");
        return customerRepository.findAll();
    }

    @Override
    public List<Customer> findAllActive() {
        log.debug("Finding all active customers");
        return customerRepository.findByActiveTrue();
    }

    @Override
    public Optional<Customer> findById(Long id) {
        log.debug("Finding customer by ID: {}", id);
        return customerRepository.findById(id);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        log.debug("Finding customer by email: {}", email);
        return customerRepository.findByEmailIgnoreCase(email);
    }

    @Override
    public Optional<Customer> findByUsername(String username) {
        log.debug("Finding customer by username: {}", username);
        return customerRepository.findByUsernameIgnoreCase(username);
    }

    @Override
    public Optional<Customer> findByUsernameOrEmail(String usernameOrEmail) {
        log.debug("Finding customer by username or email: {}", usernameOrEmail);
        return customerRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail);
    }

    @Override
    @Transactional
    public Customer create(Customer customer) {
        log.info("Creating new customer: {}", customer.getEmail());
        
        // Validate username doesn't exist
        if (customerRepository.existsByUsernameIgnoreCase(customer.getUsername())) {
            throw new IllegalArgumentException("El nombre de usuario ya está registrado");
        }
        
        // Validate email doesn't exist
        if (customerRepository.existsByEmailIgnoreCase(customer.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado");
        }
        
        // Validate phone doesn't exist
        if (customerRepository.existsByPhone(customer.getPhone())) {
            throw new IllegalArgumentException("El teléfono ya está registrado");
        }
        
        // Hash password
        if (customer.getPassword() == null || customer.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("La contraseña es requerida");
        }
        customer.setPassword(passwordEncoder.encode(customer.getPassword()));
        
        // Save customer
        Customer saved = customerRepository.save(customer);
        log.info("Customer created successfully with ID: {}", saved.getIdCustomer());
        
        return saved;
    }

    @Override
    @Transactional
    public Customer update(Long id, Customer customer) {
        log.info("Updating customer with ID: {}", id);
        
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        
        // Update all fields from the customer object
        // Controller is responsible for what fields to include
        existing.setFullName(customer.getFullName());
        existing.setUsername(customer.getUsername());
        existing.setPhone(customer.getPhone());
        existing.setAddress(customer.getAddress());
        
        // If password is provided, it should already be encoded by the controller
        if (customer.getPassword() != null && !customer.getPassword().trim().isEmpty()) {
            existing.setPassword(customer.getPassword());
        }
        // Otherwise, password remains unchanged
        
        Customer updated = customerRepository.save(existing);
        log.info("Customer updated successfully: {}", id);
        
        return updated;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deleting customer with ID: {}", id);
        
        if (!customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Cliente no encontrado");
        }
        
        customerRepository.deleteById(id);
        log.info("Customer deleted successfully: {}", id);
    }

    @Override
    public boolean existsByUsername(String username) {
        return customerRepository.existsByUsernameIgnoreCase(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return customerRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return customerRepository.existsByPhone(phone);
    }

    @Override
    @Transactional
    public void updateLastAccess(String usernameOrEmail) {
        log.debug("Updating last access for customer: {}", usernameOrEmail);
        
        customerRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail).ifPresent(customer -> {
            customer.updateLastAccess();
            customerRepository.save(customer);
        });
    }

    @Override
    @Transactional
    public Customer activate(Long id) {
        log.info("Activating customer with ID: {}", id);
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        
        customer.setActive(true);
        Customer activated = customerRepository.save(customer);
        
        log.info("Customer activated successfully: {}", id);
        return activated;
    }

    @Override
    @Transactional
    public Customer deactivate(Long id) {
        log.info("Deactivating customer with ID: {}", id);
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        
        customer.setActive(false);
        Customer deactivated = customerRepository.save(customer);
        
        log.info("Customer deactivated successfully: {}", id);
        return deactivated;
    }
}
