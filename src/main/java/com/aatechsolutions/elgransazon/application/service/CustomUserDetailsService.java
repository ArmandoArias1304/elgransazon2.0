package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Customer;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Role;
import com.aatechsolutions.elgransazon.domain.repository.CustomerRepository;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Custom UserDetailsService implementation for Spring Security
 * Loads user details from Employee or Customer entities
 * 
 * Authentication logic:
 * 1. First tries to find an Employee by username
 * 2. If not found, tries to find a Customer by email
 * 3. If neither found, throws UsernameNotFoundException
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("=== Starting authentication for user: {} ===", username);
        
        try {
            // First, try to find an Employee
            Employee employee = employeeRepository.findByUsername(username).orElse(null);
            
            if (employee != null) {
                log.info("Employee found: {} (ID: {})", employee.getFullName(), employee.getIdEmpleado());
                log.info("Employee enabled: {}", employee.getEnabled());
                
                // Try to access roles
                try {
                    log.info("Attempting to load employee roles...");
                    int rolesCount = employee.getRoles() != null ? employee.getRoles().size() : 0;
                    log.info("Roles loaded successfully. Count: {}", rolesCount);
                    
                    if (rolesCount > 0) {
                        employee.getRoles().forEach(role -> 
                            log.info("  - Role: {} (ID: {})", role.getNombreRol(), role.getIdRol())
                        );
                    } else {
                        log.warn("WARNING: Employee {} has NO ROLES assigned!", username);
                    }
                } catch (Exception e) {
                    log.error("ERROR loading employee roles: {}", e.getMessage(), e);
                    throw e;
                }

                log.info("Building UserDetails for employee...");
                UserDetails userDetails = User.builder()
                        .username(employee.getUsername())
                        .password(employee.getContrasenia())
                        .disabled(!employee.getEnabled())
                        .authorities(getEmployeeAuthorities(employee))
                        .build();
                
                log.info("=== Authentication successful for employee: {} ===", username);
                return userDetails;
            }
            
            // If not an employee, try to find a Customer by email or username
            log.info("Employee not found, trying customer...");
            Customer customer = customerRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username).orElse(null);
            
            if (customer != null) {
                log.info("Customer found: {} (ID: {})", customer.getFullName(), customer.getIdCustomer());
                log.info("Customer active: {}", customer.getActive());
                
                log.info("Building UserDetails for customer...");
                UserDetails userDetails = User.builder()
                        .username(customer.getEmail())
                        .password(customer.getPassword())
                        .disabled(!customer.getActive())
                        .authorities(getCustomerAuthorities())
                        .build();
                
                log.info("=== Authentication successful for customer: {} ===", username);
                return userDetails;
            }
            
            // Neither employee nor customer found
            log.error("User not found (neither employee nor customer): {}", username);
            throw new UsernameNotFoundException("Usuario o cliente no encontrado: " + username);
            
        } catch (UsernameNotFoundException e) {
            log.error("User not found: {}", username);
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL ERROR during authentication for user {}: {}", username, e.getMessage(), e);
            log.error("Exception type: {}", e.getClass().getName());
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage(), e.getCause());
            }
            throw new UsernameNotFoundException("Error loading user: " + username, e);
        }
    }

    /**
     * Returns the authorities/roles for an employee from their assigned roles
     */
    private Collection<? extends GrantedAuthority> getEmployeeAuthorities(Employee employee) {
        if (employee.getRoles().isEmpty()) {
            log.warn("Employee {} has no roles assigned, granting default EMPLOYEE role", employee.getUsername());
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        }

        return employee.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getNombreRol()))
                .toList();
    }
    
    /**
     * Returns the authorities/roles for a customer (always ROLE_CLIENT)
     */
    private Collection<? extends GrantedAuthority> getCustomerAuthorities() {
        return List.of(new SimpleGrantedAuthority(Role.CLIENT));
    }
}
