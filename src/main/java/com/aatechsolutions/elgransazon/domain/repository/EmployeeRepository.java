package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Employee entity
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Find an employee by their username (used for login)
     * 
     * @param username the employee's username
     * @return Optional containing the employee if found
     */
    Optional<Employee> findByUsername(String username);

    /**
     * Find an employee by their email
     * 
     * @param email the employee's email
     * @return Optional containing the employee if found
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Check if an employee exists by username
     * 
     * @param username the employee's username
     * @return true if employee exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Check if an employee exists by email
     * 
     * @param email the employee's email
     * @return true if employee exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find an employee by their phone number
     * 
     * @param telefono the employee's phone number
     * @return Optional containing the employee if found
     */
    Optional<Employee> findByTelefono(String telefono);

    /**
     * Check if an employee exists by phone number
     * 
     * @param telefono the employee's phone number
     * @return true if employee exists, false otherwise
     */
    boolean existsByTelefono(String telefono);

    /**
     * Find all enabled employees
     * 
     * @return List of enabled employees
     */
    List<Employee> findByEnabledTrue();

    /**
     * Count enabled employees
     * 
     * @return Number of enabled employees
     */
    long countByEnabledTrue();

    /**
     * Find employees by supervisor
     * 
     * @param supervisorId Supervisor's employee ID
     * @return List of employees supervised by this employee
     */
    List<Employee> findBySupervisorIdEmpleado(Long supervisorId);
}
