package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Employee management
 * Handles business logic for employee operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;

    /**
     * Find all employees
     * 
     * @return List of all employees
     */
    @Transactional(readOnly = true)
    public List<Employee> findAll() {
        log.debug("Finding all employees");
        return employeeRepository.findAll();
    }

    /**
     * Find employee by ID
     * 
     * @param id Employee ID
     * @return Optional containing the employee if found
     */
    @Transactional(readOnly = true)
    public Optional<Employee> findById(Long id) {
        log.debug("Finding employee by id: {}", id);
        return employeeRepository.findById(id);
    }

    /**
     * Find employee by username
     * 
     * @param username Employee's username
     * @return Optional containing the employee if found
     */
    @Transactional(readOnly = true)
    public Optional<Employee> findByUsername(String username) {
        log.debug("Finding employee by username: {}", username);
        return employeeRepository.findByUsername(username);
    }

    /**
     * Find employee by email
     * 
     * @param email Employee's email
     * @return Optional containing the employee if found
     */
    @Transactional(readOnly = true)
    public Optional<Employee> findByEmail(String email) {
        log.debug("Finding employee by email: {}", email);
        return employeeRepository.findByEmail(email);
    }

    /**
     * Create a new employee
     * Encodes the password before saving
     * Sets admin as supervisor by default if no supervisor is provided
     * 
     * @param employee Employee to create
     * @param createdBy Username of the user creating this employee
     * @return Created employee
     * @throws IllegalArgumentException if employee with same username or email already exists
     */
    @Transactional
    public Employee create(Employee employee, String createdBy) {
        log.info("Creating new employee: {} by {}", employee.getUsername(), createdBy);

        if (employeeRepository.existsByUsername(employee.getUsername())) {
            log.error("Employee with username {} already exists", employee.getUsername());
            throw new IllegalArgumentException("El usuario '" + employee.getUsername() + "' ya existe");
        }

        if (employeeRepository.existsByEmail(employee.getEmail())) {
            log.error("Employee with email {} already exists", employee.getEmail());
            throw new IllegalArgumentException("El email '" + employee.getEmail() + "' ya existe");
        }

        // Check if phone number is already taken (if provided)
        if (employee.getTelefono() != null && !employee.getTelefono().isEmpty() &&
            employeeRepository.existsByTelefono(employee.getTelefono())) {
            log.error("Employee with phone {} already exists", employee.getTelefono());
            throw new IllegalArgumentException("El teléfono '" + employee.getTelefono() + "' ya está registrado");
        }

        // Encode password before saving
        String encodedPassword = passwordEncoder.encode(employee.getContrasenia());
        employee.setContrasenia(encodedPassword);

        // Set audit fields
        employee.setCreatedBy(createdBy);
        employee.setUpdatedBy(createdBy);

        // Set admin as supervisor by default if no supervisor is provided
        if (employee.getSupervisor() == null) {
            Optional<Employee> admin = employeeRepository.findByUsername("admin");
            admin.ifPresent(employee::setSupervisor);
            log.debug("Set admin as default supervisor for employee: {}", employee.getUsername());
        }

        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with id: {}", savedEmployee.getIdEmpleado());
        
        return savedEmployee;
    }

    /**
     * Update an existing employee
     * 
     * @param id Employee ID to update
     * @param employeeDetails Updated employee details
     * @param updatedBy Username of the user updating this employee
     * @return Updated employee
     * @throws IllegalArgumentException if employee not found
     */
    @Transactional
    public Employee update(Long id, Employee employeeDetails, String updatedBy) {
        log.info("Updating employee with id: {} by {}", id, updatedBy);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Employee not found with id: {}", id);
                    return new IllegalArgumentException("Empleado no encontrado con id: " + id);
                });

        // Get current user to check if it's a MANAGER
        Employee currentUser = employeeRepository.findByUsername(updatedBy)
                .orElseThrow(() -> new IllegalStateException("Usuario actual no encontrado"));
        
        boolean isManager = currentUser.hasRole(com.aatechsolutions.elgransazon.domain.entity.Role.MANAGER);

        // Track if username is changing
        boolean usernameChanged = !employee.getUsername().equals(employeeDetails.getUsername());
        String oldUsername = employee.getUsername();
        
        // Check if username is being changed and if it's already taken
        if (usernameChanged && employeeRepository.existsByUsername(employeeDetails.getUsername())) {
            log.error("Username {} already exists", employeeDetails.getUsername());
            throw new IllegalArgumentException("El usuario '" + employeeDetails.getUsername() + "' ya existe");
        }

        // Check if email is being changed and if it's already taken
        if (!employee.getEmail().equals(employeeDetails.getEmail()) &&
            employeeRepository.existsByEmail(employeeDetails.getEmail())) {
            log.error("Email {} already exists", employeeDetails.getEmail());
            throw new IllegalArgumentException("El email '" + employeeDetails.getEmail() + "' ya existe");
        }

        // Check if phone is being changed and if it's already taken (if provided)
        if (employeeDetails.getTelefono() != null && !employeeDetails.getTelefono().isEmpty() &&
            !employeeDetails.getTelefono().equals(employee.getTelefono()) &&
            employeeRepository.existsByTelefono(employeeDetails.getTelefono())) {
            log.error("Phone {} already exists", employeeDetails.getTelefono());
            throw new IllegalArgumentException("El teléfono '" + employeeDetails.getTelefono() + "' ya está registrado");
        }

        // Update basic fields
        employee.setUsername(employeeDetails.getUsername());
        employee.setNombre(employeeDetails.getNombre());
        employee.setApellido(employeeDetails.getApellido());
        employee.setEmail(employeeDetails.getEmail());
        employee.setTelefono(employeeDetails.getTelefono());
        
        // MANAGER restrictions: cannot modify roles, salary, supervisor, or enabled status
        if (isManager) {
            log.info("MANAGER updating employee - preserving roles, salary, supervisor, and enabled status");
            // Keep original values for restricted fields
            // Roles, Salary, Supervisor, and Enabled status are NOT updated
        } else {
            // ADMIN can update everything
            employee.setSalario(employeeDetails.getSalario());
            employee.setSupervisor(employeeDetails.getSupervisor());
            employee.setRoles(employeeDetails.getRoles());
            employee.setEnabled(employeeDetails.getEnabled());
        }
        
        // Update audit field
        employee.setUpdatedBy(updatedBy);

        // Track if password is being changed
        boolean passwordChanged = false;
        
        // Only update password if it's provided and different
        if (employeeDetails.getContrasenia() != null && 
            !employeeDetails.getContrasenia().isEmpty() &&
            !employee.getContrasenia().equals(employeeDetails.getContrasenia())) {
            String encodedPassword = passwordEncoder.encode(employeeDetails.getContrasenia());
            employee.setContrasenia(encodedPassword);
            passwordChanged = true;
        }

        Employee updatedEmployee = employeeRepository.save(employee);
        log.info("Employee updated successfully: {}", updatedEmployee.getIdEmpleado());
        
        // Invalidate sessions if username was changed
        if (usernameChanged) {
            invalidateUserSessions(oldUsername);
            log.info("Invalidated sessions for user {} due to username change", oldUsername);
        }
        
        // Invalidate sessions if password was changed
        if (passwordChanged) {
            invalidateUserSessions(updatedEmployee.getUsername());
            log.info("Invalidated sessions for user {} due to password change", updatedEmployee.getUsername());
        }
        
        return updatedEmployee;
    }

    /**
     * Delete an employee
     * 
     * @param id Employee ID to delete
     * @throws IllegalArgumentException if employee not found
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting employee with id: {}", id);

        if (!employeeRepository.existsById(id)) {
            log.error("Employee not found with id: {}", id);
            throw new IllegalArgumentException("Employee not found with id: " + id);
        }

        employeeRepository.deleteById(id);
        log.info("Employee deleted successfully: {}", id);
    }

    /**
     * Change employee password
     * 
     * @param id Employee ID
     * @param newPassword New password (plain text)
     * @throws IllegalArgumentException if employee not found
     */
    @Transactional
    public void changePassword(Long id, String newPassword) {
        log.info("Changing password for employee with id: {}", id);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Employee not found with id: {}", id);
                    return new IllegalArgumentException("Employee not found with id: " + id);
                });

        String encodedPassword = passwordEncoder.encode(newPassword);
        employee.setContrasenia(encodedPassword);
        
        employeeRepository.save(employee);
        log.info("Password changed successfully for employee: {}", id);
        
        // Invalidate sessions after password change
        invalidateUserSessions(employee.getUsername());
        log.info("Invalidated sessions for user {} due to password change", employee.getUsername());
    }

    /**
     * Enable or disable an employee
     * 
     * @param id Employee ID
     * @param enabled Enable status
     * @param updatedBy Username of the user updating this employee
     * @throws IllegalArgumentException if employee not found
     */
    @Transactional
    public void setEnabled(Long id, boolean enabled, String updatedBy) {
        log.info("Setting enabled status to {} for employee with id: {} by {}", enabled, id, updatedBy);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Employee not found with id: {}", id);
                    return new IllegalArgumentException("Empleado no encontrado con id: " + id);
                });

        employee.setEnabled(enabled);
        employee.setUpdatedBy(updatedBy);
        employeeRepository.save(employee);
        
        log.info("Employee enabled status updated: {}", id);
        
        // Invalidate sessions if employee was disabled
        if (!enabled) {
            invalidateUserSessions(employee.getUsername());
            log.info("Invalidated sessions for user {} due to account being disabled", employee.getUsername());
        }
    }

    /**
     * Update employee's last access time
     * 
     * @param username Employee's username
     */
    @Transactional
    public void updateLastAccess(String username) {
        log.debug("Updating last access for user: {}", username);
        
        Optional<Employee> employeeOpt = employeeRepository.findByUsername(username);
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            employee.setLastAccess(java.time.LocalDateTime.now());
            employeeRepository.save(employee);
            log.debug("Last access updated for user: {}", username);
        }
    }

    /**
     * Find all enabled employees
     * 
     * @return List of enabled employees
     */
    @Transactional(readOnly = true)
    public List<Employee> findAllEnabled() {
        log.debug("Finding all enabled employees");
        return employeeRepository.findByEnabledTrue();
    }

    /**
     * Find employees by role
     * 
     * @param roleName Role name (e.g., "ROLE_WAITER")
     * @return List of employees with that role
     */
    @Transactional(readOnly = true)
    public List<Employee> findByRole(String roleName) {
        log.debug("Finding employees by role: {}", roleName);
        return employeeRepository.findAll().stream()
                .filter(e -> e.hasRole(roleName))
                .toList();
    }

    /**
     * Count all employees
     * 
     * @return Total number of employees
     */
    @Transactional(readOnly = true)
    public long countAll() {
        return employeeRepository.count();
    }

    /**
     * Count enabled employees
     * 
     * @return Number of enabled employees
     */
    @Transactional(readOnly = true)
    public long countEnabled() {
        return employeeRepository.countByEnabledTrue();
    }

    /**
     * Find employees supervised by a specific employee
     * 
     * @param supervisorId Supervisor's employee ID
     * @return List of employees supervised by this employee
     */
    @Transactional(readOnly = true)
    public List<Employee> findBySupervisor(Long supervisorId) {
        log.debug("Finding employees supervised by: {}", supervisorId);
        return employeeRepository.findBySupervisorIdEmpleado(supervisorId);
    }

    /**
     * Invalidate all active sessions for a user
     * Forces the user to re-authenticate
     * 
     * @param username Username of the user whose sessions should be invalidated
     */
    private void invalidateUserSessions(String username) {
        log.debug("Invalidating sessions for user: {}", username);
        
        try {
            List<Object> principals = sessionRegistry.getAllPrincipals();
            
            for (Object principal : principals) {
                if (principal instanceof UserDetails) {
                    UserDetails userDetails = (UserDetails) principal;
                    
                    if (userDetails.getUsername().equals(username)) {
                        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
                        
                        for (SessionInformation session : sessions) {
                            session.expireNow();
                            log.info("Expired session {} for user {}", session.getSessionId(), username);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error invalidating sessions for user {}: {}", username, e.getMessage(), e);
        }
    }
}
