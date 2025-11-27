-- ============================================================================
-- Script de Inicialización para el Módulo de Turnos (Shifts)
-- Base de Datos: bd_restaurant
-- Descripción: Crea las tablas necesarias para gestionar los turnos de empleados
--              y su historial de asignaciones/remociones
-- ============================================================================

USE bd_restaurant;

-- ============================================================================
-- Tabla: shift
-- Descripción: Almacena los turnos de trabajo disponibles en el restaurante
-- ============================================================================
CREATE TABLE IF NOT EXISTS shift (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_shift_time CHECK (end_time > start_time),
    
    -- Indexes
    INDEX idx_shift_name (name),
    INDEX idx_shift_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Turnos de trabajo del restaurante';

-- ============================================================================
-- Tabla: shift_work_days
-- Descripción: Almacena los días laborales de cada turno (relación ElementCollection)
-- ============================================================================
CREATE TABLE IF NOT EXISTS shift_work_days (
    shift_id BIGINT NOT NULL,
    work_days VARCHAR(20) NOT NULL,
    
    -- Foreign Keys
    CONSTRAINT fk_shift_work_days_shift 
        FOREIGN KEY (shift_id) REFERENCES shift(id) 
        ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_shift_work_days_shift (shift_id),
    INDEX idx_shift_work_days_day (work_days)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Días laborales de cada turno';

-- ============================================================================
-- Tabla: shift_employees
-- Descripción: Tabla de unión para la relación ManyToMany entre Shift y Employee
-- ============================================================================
CREATE TABLE IF NOT EXISTS shift_employees (
    shift_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    
    PRIMARY KEY (shift_id, employee_id),
    
    -- Foreign Keys
    CONSTRAINT fk_shift_employees_shift 
        FOREIGN KEY (shift_id) REFERENCES shift(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_shift_employees_employee 
        FOREIGN KEY (employee_id) REFERENCES employee(id_empleado) 
        ON DELETE CASCADE,
    
    -- Indexes
    INDEX idx_shift_employees_shift (shift_id),
    INDEX idx_shift_employees_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Relación entre turnos y empleados';

-- ============================================================================
-- Tabla: employee_shift_history
-- Descripción: Historial de asignaciones y remociones de empleados en turnos
-- ============================================================================
CREATE TABLE IF NOT EXISTS employee_shift_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    shift_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    action_by_id BIGINT,
    action_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500),
    
    -- Foreign Keys
    CONSTRAINT fk_history_employee 
        FOREIGN KEY (employee_id) REFERENCES employee(id_empleado) 
        ON DELETE CASCADE,
    CONSTRAINT fk_history_shift 
        FOREIGN KEY (shift_id) REFERENCES shift(id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_history_action_by 
        FOREIGN KEY (action_by_id) REFERENCES employee(id_empleado) 
        ON DELETE SET NULL,
    
    -- Constraints
    CONSTRAINT chk_history_action CHECK (action IN ('ASSIGNED', 'REMOVED')),
    
    -- Indexes
    INDEX idx_history_employee (employee_id),
    INDEX idx_history_shift (shift_id),
    INDEX idx_history_action (action),
    INDEX idx_history_action_by (action_by_id),
    INDEX idx_history_action_date (action_date),
    INDEX idx_history_employee_shift (employee_id, shift_id),
    INDEX idx_history_composite (employee_id, shift_id, action_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Historial de cambios en asignación de turnos';

-- ============================================================================
-- Datos de Ejemplo (Opcional)
-- Descripción: Inserta turnos de ejemplo si no existen
-- ============================================================================

-- Turno Mañana
INSERT INTO shift (name, description, start_time, end_time, active)
SELECT 'Turno Mañana', 'Turno de mañana para atención al cliente', '08:00:00', '14:00:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM shift WHERE name = 'Turno Mañana');

-- Obtener el ID del turno recién creado
SET @turno_manana_id = (SELECT id FROM shift WHERE name = 'Turno Mañana');

-- Asignar días laborales al Turno Mañana (Lunes a Viernes)
INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_manana_id, 'MONDAY'
WHERE @turno_manana_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_manana_id AND work_days = 'MONDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_manana_id, 'TUESDAY'
WHERE @turno_manana_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_manana_id AND work_days = 'TUESDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_manana_id, 'WEDNESDAY'
WHERE @turno_manana_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_manana_id AND work_days = 'WEDNESDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_manana_id, 'THURSDAY'
WHERE @turno_manana_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_manana_id AND work_days = 'THURSDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_manana_id, 'FRIDAY'
WHERE @turno_manana_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_manana_id AND work_days = 'FRIDAY');

-- Turno Tarde
INSERT INTO shift (name, description, start_time, end_time, active)
SELECT 'Turno Tarde', 'Turno de tarde para atención al cliente', '14:00:00', '20:00:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM shift WHERE name = 'Turno Tarde');

-- Obtener el ID del turno recién creado
SET @turno_tarde_id = (SELECT id FROM shift WHERE name = 'Turno Tarde');

-- Asignar días laborales al Turno Tarde (Lunes a Viernes)
INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_tarde_id, 'MONDAY'
WHERE @turno_tarde_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_tarde_id AND work_days = 'MONDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_tarde_id, 'TUESDAY'
WHERE @turno_tarde_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_tarde_id AND work_days = 'TUESDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_tarde_id, 'WEDNESDAY'
WHERE @turno_tarde_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_tarde_id AND work_days = 'WEDNESDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_tarde_id, 'THURSDAY'
WHERE @turno_tarde_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_tarde_id AND work_days = 'THURSDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_tarde_id, 'FRIDAY'
WHERE @turno_tarde_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_tarde_id AND work_days = 'FRIDAY');

-- Turno Fin de Semana
INSERT INTO shift (name, description, start_time, end_time, active)
SELECT 'Turno Fin de Semana', 'Turno especial para fines de semana', '10:00:00', '18:00:00', TRUE
WHERE NOT EXISTS (SELECT 1 FROM shift WHERE name = 'Turno Fin de Semana');

-- Obtener el ID del turno recién creado
SET @turno_weekend_id = (SELECT id FROM shift WHERE name = 'Turno Fin de Semana');

-- Asignar días laborales al Turno Fin de Semana (Sábado y Domingo)
INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_weekend_id, 'SATURDAY'
WHERE @turno_weekend_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_weekend_id AND work_days = 'SATURDAY');

INSERT INTO shift_work_days (shift_id, work_days)
SELECT @turno_weekend_id, 'SUNDAY'
WHERE @turno_weekend_id IS NOT NULL 
AND NOT EXISTS (SELECT 1 FROM shift_work_days WHERE shift_id = @turno_weekend_id AND work_days = 'SUNDAY');

-- ============================================================================
-- Verificación
-- ============================================================================
SELECT 'Módulo de Turnos inicializado correctamente' AS mensaje;
SELECT COUNT(*) AS total_turnos FROM shift;
SELECT COUNT(*) AS total_dias_configurados FROM shift_work_days;
SELECT COUNT(*) AS total_historial FROM employee_shift_history;

-- ============================================================================
-- Queries de Consulta Útiles (Comentadas)
-- ============================================================================

-- Ver todos los turnos con sus días laborales
-- SELECT s.id, s.name, s.start_time, s.end_time, s.active, 
--        GROUP_CONCAT(swd.work_days ORDER BY swd.work_days) AS work_days
-- FROM shift s
-- LEFT JOIN shift_work_days swd ON s.id = swd.shift_id
-- GROUP BY s.id, s.name, s.start_time, s.end_time, s.active
-- ORDER BY s.name;

-- Ver empleados asignados por turno
-- SELECT s.name AS turno, 
--        COUNT(se.employee_id) AS total_empleados,
--        GROUP_CONCAT(e.nombre, ' ', e.apellido SEPARATOR ', ') AS empleados
-- FROM shift s
-- LEFT JOIN shift_employees se ON s.id = se.shift_id
-- LEFT JOIN employee e ON se.employee_id = e.id_empleado
-- GROUP BY s.id, s.name
-- ORDER BY s.name;

-- Ver historial de cambios recientes
-- SELECT esh.action_date,
--        CONCAT(e.nombre, ' ', e.apellido) AS empleado,
--        s.name AS turno,
--        esh.action,
--        CONCAT(ab.nombre, ' ', ab.apellido) AS realizado_por,
--        esh.reason
-- FROM employee_shift_history esh
-- INNER JOIN employee e ON esh.employee_id = e.id_empleado
-- INNER JOIN shift s ON esh.shift_id = s.id
-- LEFT JOIN employee ab ON esh.action_by_id = ab.id_empleado
-- ORDER BY esh.action_date DESC
-- LIMIT 20;
