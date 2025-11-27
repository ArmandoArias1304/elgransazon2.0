package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.Supplier;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service implementation for Supplier management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> findAll() {
        log.info("Finding all suppliers");
        return supplierRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findById(Long id) {
        log.info("Finding supplier by id: {}", id);
        return supplierRepository.findById(id);
    }

    @Override
    @Transactional
    public Supplier create(Supplier supplier) {
        log.info("Creating new supplier: {}", supplier.getName());

        // Validate unique name
        if (supplierRepository.existsByName(supplier.getName())) {
            log.error("Supplier with name {} already exists", supplier.getName());
            throw new IllegalArgumentException("Ya existe un proveedor con el nombre: " + supplier.getName());
        }

        // Validate unique email if provided
        if (supplier.getEmail() != null && !supplier.getEmail().isEmpty() &&
            supplierRepository.existsByEmail(supplier.getEmail())) {
            log.error("Supplier with email {} already exists", supplier.getEmail());
            throw new IllegalArgumentException("Ya existe un proveedor con el email: " + supplier.getEmail());
        }

        // Set created by (authenticated user)
        setCreatedBy(supplier);

        Supplier savedSupplier = supplierRepository.save(supplier);
        log.info("Supplier created successfully with id: {}", savedSupplier.getIdSupplier());
        return savedSupplier;
    }

    @Override
    @Transactional
    public Supplier update(Long id, Supplier supplierDetails) {
        log.info("Updating supplier with id: {}", id);

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Supplier not found with id: {}", id);
                    return new IllegalArgumentException("Proveedor no encontrado con id: " + id);
                });

        // Validate unique name if changed
        if (!supplier.getName().equals(supplierDetails.getName()) &&
            supplierRepository.existsByName(supplierDetails.getName())) {
            log.error("Supplier with name {} already exists", supplierDetails.getName());
            throw new IllegalArgumentException("Ya existe un proveedor con el nombre: " + supplierDetails.getName());
        }

        // Validate unique email if changed
        if (supplierDetails.getEmail() != null && !supplierDetails.getEmail().isEmpty() &&
            !supplierDetails.getEmail().equals(supplier.getEmail()) &&
            supplierRepository.existsByEmail(supplierDetails.getEmail())) {
            log.error("Supplier with email {} already exists", supplierDetails.getEmail());
            throw new IllegalArgumentException("Ya existe un proveedor con el email: " + supplierDetails.getEmail());
        }

        // Update fields
        supplier.setName(supplierDetails.getName());
        supplier.setContactPerson(supplierDetails.getContactPerson());
        supplier.setPhone(supplierDetails.getPhone());
        supplier.setEmail(supplierDetails.getEmail());
        supplier.setAddress(supplierDetails.getAddress());
        supplier.setNotes(supplierDetails.getNotes());
        supplier.setRating(supplierDetails.getRating());
        supplier.setActive(supplierDetails.getActive());

        // Update categories (ManyToMany relationship)
        supplier.getCategories().clear();
        if (supplierDetails.getCategories() != null) {
            supplier.getCategories().addAll(supplierDetails.getCategories());
        }

        Supplier updatedSupplier = supplierRepository.save(supplier);
        log.info("Supplier updated successfully: {}", id);
        return updatedSupplier;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Deactivating supplier with id: {}", id);

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Supplier not found with id: {}", id);
                    return new IllegalArgumentException("Proveedor no encontrado con id: " + id);
                });

        supplier.setActive(false);
        supplierRepository.save(supplier);
        log.info("Supplier deactivated successfully: {}", id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        log.info("Activating supplier with id: {}", id);

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Supplier not found with id: {}", id);
                    return new IllegalArgumentException("Proveedor no encontrado con id: " + id);
                });

        supplier.setActive(true);
        supplierRepository.save(supplier);
        log.info("Supplier activated successfully: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> searchWithFilters(String search, Integer rating, Long categoryId, Boolean active) {
        log.info("Searching suppliers with filters - search: {}, rating: {}, categoryId: {}, active: {}",
                search, rating, categoryId, active);

        // Normalize search string
        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        List<Supplier> suppliers = supplierRepository.searchWithFilters(normalizedSearch, rating, categoryId, active);
        log.info("Found {} suppliers with filters", suppliers.size());
        return suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> findByCategoryId(Long categoryId) {
        log.info("Finding suppliers for category ID: {}", categoryId);

        List<Supplier> suppliers = supplierRepository.findByCategoriesIdCategory(categoryId);

        // Sort alphabetically
        suppliers.sort(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER));

        log.info("Found {} suppliers for category ID: {}", suppliers.size(), categoryId);
        return suppliers;
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveCount() {
        return supplierRepository.countByActive(true);
    }

    @Override
    @Transactional(readOnly = true)
    public long getInactiveCount() {
        return supplierRepository.countByActive(false);
    }

    /**
     * Set the created by field with the authenticated user
     */
    private void setCreatedBy(Supplier supplier) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

                String username = authentication.getName();
                Optional<Employee> employee = employeeRepository.findByUsername(username);

                employee.ifPresent(supplier::setCreatedBy);
            }
        } catch (Exception e) {
            log.warn("Could not set created by: {}", e.getMessage());
        }
    }

    @Override
    public List<Supplier> findAllActive() {
        log.info("Finding all active suppliers");
        return supplierRepository.findAllActive();
    }
}
