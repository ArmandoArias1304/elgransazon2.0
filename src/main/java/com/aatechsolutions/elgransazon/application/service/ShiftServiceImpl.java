package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.*;
import com.aatechsolutions.elgransazon.domain.repository.EmployeeRepository;
import com.aatechsolutions.elgransazon.domain.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of ShiftService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShiftServiceImpl implements ShiftService {

    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemConfigurationService configurationService;
    private final BusinessHoursService businessHoursService;
    private final EmployeeShiftHistoryService historyService;

    @Override
    @Transactional(readOnly = true)
    public List<Shift> getAllShifts() {
        log.debug("Fetching all shifts");
        return shiftRepository.findAllOrderByName();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Shift> getAllActiveShifts() {
        log.debug("Fetching all active shifts");
        return shiftRepository.findByActiveTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shift> getShiftById(Long id) {
        log.debug("Fetching shift by ID: {}", id);
        return shiftRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Shift> getShiftByName(String name) {
        log.debug("Fetching shift by name: {}", name);
        return shiftRepository.findByNameIgnoreCase(name);
    }

    @Override
    public Shift createShift(Shift shift) {
        log.info("Creating shift: {}", shift.getName());

        // Validate shift name uniqueness
        if (shiftNameExists(shift.getName())) {
            throw new IllegalArgumentException("Ya existe un turno con el nombre: " + shift.getName());
        }

        // Validate shift days and hours
        validateShift(shift);

        Shift saved = shiftRepository.save(shift);
        log.info("Shift created successfully with ID: {}", saved.getId());
        return saved;
    }

    @Override
    public Shift updateShift(Long id, Shift shift) {
        log.info("Updating shift with ID: {}", id);

        Shift existingShift = shiftRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado con ID: " + id));

        // Validate name uniqueness if changed
        if (!existingShift.getName().equalsIgnoreCase(shift.getName())) {
            if (shiftNameExistsExcludingId(shift.getName(), id)) {
                throw new IllegalArgumentException("Ya existe un turno con el nombre: " + shift.getName());
            }
        }

        // Validate shift days and hours
        validateShift(shift);

        // Update fields
        existingShift.setName(shift.getName());
        existingShift.setDescription(shift.getDescription());
        existingShift.setStartTime(shift.getStartTime());
        existingShift.setEndTime(shift.getEndTime());
        existingShift.setWorkDays(shift.getWorkDays());
        existingShift.setActive(shift.getActive());

        Shift saved = shiftRepository.save(existingShift);
        log.info("Shift updated successfully");
        return saved;
    }

    @Override
    public void deleteShift(Long id) {
        log.info("Deleting shift with ID: {}", id);

        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado con ID: " + id));

        // Check if shift has employees assigned
        if (shift.hasEmployees()) {
            throw new IllegalStateException(
                    "No se puede eliminar el turno porque tiene " + shift.getEmployeeCount() + 
                    " empleados asignados. Remueva los empleados primero."
            );
        }

        shiftRepository.delete(shift);
        log.info("Shift deleted successfully");
    }

    @Override
    public void activateShift(Long id) {
        log.info("Activating shift with ID: {}", id);

        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado con ID: " + id));

        shift.setActive(true);
        shiftRepository.save(shift);
        log.info("Shift activated successfully");
    }

    @Override
    public void deactivateShift(Long id) {
        log.info("Deactivating shift with ID: {}", id);

        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado con ID: " + id));

        shift.setActive(false);
        shiftRepository.save(shift);
        log.info("Shift deactivated successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean shiftNameExists(String name) {
        return shiftRepository.existsByNameIgnoreCase(name);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean shiftNameExistsExcludingId(String name, Long id) {
        return shiftRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveShifts() {
        return shiftRepository.countActiveShifts();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAllShifts() {
        return shiftRepository.countAllShifts();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Shift> getShiftsByEmployee(Long employeeId) {
        log.debug("Fetching shifts for employee ID: {}", employeeId);
        return shiftRepository.findShiftsForEmployee(employeeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Employee> getEmployeesByShift(Long shiftId) {
        log.debug("Fetching employees for shift ID: {}", shiftId);
        
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado con ID: " + shiftId));
        
        return new ArrayList<>(shift.getEmployees());
    }

    @Override
    public void assignEmployeesToShift(Long shiftId, List<Long> employeeIds, Long actionById) {
        log.info("Assigning {} employees to shift ID: {}", employeeIds.size(), shiftId);

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado con ID: " + shiftId));

        if (!shift.getActive()) {
            throw new IllegalStateException("No se pueden asignar empleados a un turno inactivo");
        }

        Employee actionBy = actionById != null ? 
                employeeRepository.findById(actionById).orElse(null) : null;

        List<Employee> assignedEmployees = new ArrayList<>();

        for (Long employeeId : employeeIds) {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + employeeId));

            if (!employee.getEnabled()) {
                log.warn("Skipping disabled employee: {}", employee.getFullName());
                continue;
            }

            // Check if already assigned
            if (shift.hasEmployee(employee)) {
                log.debug("Employee {} already assigned to shift", employee.getFullName());
                continue;
            }

            shift.addEmployee(employee);
            assignedEmployees.add(employee);
        }

        if (!assignedEmployees.isEmpty()) {
            shiftRepository.save(shift);
            
            // Create history records
            historyService.createHistoryRecords(
                    assignedEmployees, 
                    shift, 
                    ShiftAction.ASSIGNED, 
                    actionBy, 
                    null
            );
            
            log.info("Successfully assigned {} employees to shift", assignedEmployees.size());
        } else {
            log.info("No new employees were assigned");
        }
    }

    @Override
    public void removeEmployeesFromShift(Long shiftId, List<Long> employeeIds, Long actionById, String reason) {
        log.info("Removing {} employees from shift ID: {}", employeeIds.size(), shiftId);

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new IllegalArgumentException("Turno no encontrado con ID: " + shiftId));

        Employee actionBy = actionById != null ? 
                employeeRepository.findById(actionById).orElse(null) : null;

        List<Employee> removedEmployees = new ArrayList<>();

        for (Long employeeId : employeeIds) {
            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con ID: " + employeeId));

            if (!shift.hasEmployee(employee)) {
                log.debug("Employee {} not assigned to shift", employee.getFullName());
                continue;
            }

            shift.removeEmployee(employee);
            removedEmployees.add(employee);
        }

        if (!removedEmployees.isEmpty()) {
            shiftRepository.save(shift);
            
            // Create history records
            historyService.createHistoryRecords(
                    removedEmployees, 
                    shift, 
                    ShiftAction.REMOVED, 
                    actionBy, 
                    reason
            );
            
            log.info("Successfully removed {} employees from shift", removedEmployees.size());
        } else {
            log.info("No employees were removed");
        }
    }

    @Override
    public void assignEmployeeToShift(Long shiftId, Long employeeId, Long actionById) {
        assignEmployeesToShift(shiftId, List.of(employeeId), actionById);
    }

    @Override
    public void removeEmployeeFromShift(Long shiftId, Long employeeId, Long actionById, String reason) {
        removeEmployeesFromShift(shiftId, List.of(employeeId), actionById, reason);
    }

    @Override
    public void validateShiftDays(Set<DayOfWeek> shiftDays) {
        log.debug("Validating shift days");

        if (shiftDays == null || shiftDays.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un día para el turno");
        }

        SystemConfiguration config = configurationService.getConfiguration();
        
        // Get work days from business hours (days where is_closed = false)
        List<DayOfWeek> workDays = config.getSortedWorkDays();

        if (workDays == null || workDays.isEmpty()) {
            throw new IllegalStateException(
                    "No hay días laborales configurados en el sistema. Configure los horarios de negocio primero."
            );
        }

        // Validate each shift day is a work day
        for (DayOfWeek day : shiftDays) {
            if (!config.isWorkDay(day)) {
                throw new IllegalArgumentException(
                        "El día " + day.getDisplayName() + " no es un día laboral del restaurante. " +
                        "El restaurante está cerrado este día."
                );
            }
        }

        log.debug("Shift days validation passed");
    }

    @Override
    public void validateShiftHours(Set<DayOfWeek> shiftDays, LocalTime startTime, LocalTime endTime) {
        log.debug("Validating shift hours");

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Debe especificar hora de inicio y fin del turno");
        }

        if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
            throw new IllegalArgumentException("La hora de fin debe ser después de la hora de inicio");
        }

        // Validate hours for each day
        for (DayOfWeek day : shiftDays) {
            Optional<BusinessHours> businessHours = businessHoursService.getBusinessHoursForDay(day);

            if (businessHours.isEmpty()) {
                throw new IllegalStateException(
                        "No hay horarios configurados para el día " + day.getDisplayName() + 
                        ". Configure los horarios del restaurante primero."
                );
            }

            BusinessHours hours = businessHours.get();

            if (hours.getIsClosed()) {
                throw new IllegalArgumentException(
                        "El restaurante está cerrado el día " + day.getDisplayName()
                );
            }

            // Validate start time
            if (startTime.isBefore(hours.getOpenTime())) {
                throw new IllegalArgumentException(
                        "La hora de inicio del turno (" + startTime + ") para " + day.getDisplayName() + 
                        " debe ser a partir de las " + hours.getOpenTime()
                );
            }

            // Validate end time
            if (endTime.isAfter(hours.getCloseTime())) {
                throw new IllegalArgumentException(
                        "La hora de fin del turno (" + endTime + ") para " + day.getDisplayName() + 
                        " debe ser antes de las " + hours.getCloseTime()
                );
            }
        }

        log.debug("Shift hours validation passed");
    }

    @Override
    public void validateShift(Shift shift) {
        log.debug("Validating complete shift");

        if (shift == null) {
            throw new IllegalArgumentException("El turno no puede ser nulo");
        }

        // Validate days
        validateShiftDays(shift.getWorkDays());

        // Validate hours
        validateShiftHours(shift.getWorkDays(), shift.getStartTime(), shift.getEndTime());

        log.debug("Complete shift validation passed");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Shift> getShiftsWithoutEmployees() {
        log.debug("Fetching shifts without employees");
        return shiftRepository.findShiftsWithoutEmployees();
    }

    @Override
    @Transactional(readOnly = true)
    public long countEmployeesInShift(Long shiftId) {
        return shiftRepository.countEmployeesByShiftId(shiftId);
    }
}
