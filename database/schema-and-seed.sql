

CREATE DATABASE IF NOT EXISTS salgueros_asistencia 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE salgueros_asistencia;

-- Desactivar llaves foráneas temporalmente para recreación limpia
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS registros_asistencia;
DROP TABLE IF EXISTS programacion_semanal;
DROP TABLE IF EXISTS vectores_faciales;
DROP TABLE IF EXISTS empleados;
SET FOREIGN_KEY_CHECKS = 1;

-- 1. Tabla de Empleados
CREATE TABLE empleados (
    id_empleado INT AUTO_INCREMENT PRIMARY KEY,
    cedula VARCHAR(20) NOT NULL UNIQUE,
    nombre_apellido VARCHAR(100) NOT NULL,
    area VARCHAR(50) NOT NULL DEFAULT 'PLANTA',
    estado ENUM('ACTIVO', 'INACTIVO', 'EN_PROCESO_RETIRO') DEFAULT 'ACTIVO'
) ENGINE=InnoDB;

-- 2. Tabla de Vectores Faciales (128 flotantes numéricos - RNF-03)
CREATE TABLE vectores_faciales (
    id_vector INT AUTO_INCREMENT PRIMARY KEY,
    id_empleado INT NOT NULL,
    vector_embedding JSON NOT NULL,
    CONSTRAINT fk_vector_empleado FOREIGN KEY (id_empleado) 
        REFERENCES empleados(id_empleado) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. Tabla de Programación Semanal (Fuente de Verdad)
CREATE TABLE programacion_semanal (
    id_programacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_empleado INT NOT NULL,
    fecha DATE NOT NULL,
    hora_entrada_programada TIME NOT NULL,
    minutos_almuerzo_programados INT DEFAULT 60,
    horas_extras_autorizadas BOOLEAN DEFAULT FALSE,
    tipo_novedad_precargada ENUM('NINGUNA', 'INCAPACIDAD', 'PERMISO', 'VACACIONES', 'RETIRO') DEFAULT 'NINGUNA',
    CONSTRAINT fk_prog_empleado FOREIGN KEY (id_empleado) 
        REFERENCES empleados(id_empleado),
    UNIQUE KEY uq_empleado_fecha (id_empleado, fecha)
) ENGINE=InnoDB;

-- 4. Tabla de Registros de Asistencia
CREATE TABLE registros_asistencia (
    id_registro BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_programacion BIGINT NOT NULL UNIQUE,
    hora_entrada_real DATETIME NOT NULL,
    hora_salida_almuerzo DATETIME NULL,
    hora_regreso_almuerzo DATETIME NULL,
    hora_salida_real DATETIME NULL,
    minutos_retardo INT DEFAULT 0,
    estado_llegada ENUM('A_TIEMPO', 'RETARDO', 'NO_APLICA') NOT NULL,
    estado_dia ENUM('EN_CURSO', 'COMPLETADO', 'FALTO', 'NOVEDAD_JUSTIFICADA') DEFAULT 'EN_CURSO',
    CONSTRAINT fk_registro_programacion FOREIGN KEY (id_programacion) 
        REFERENCES programacion_semanal(id_programacion)
) ENGINE=InnoDB;



-- Empleados
INSERT INTO empleados (id_empleado, cedula, nombre_apellido, estado) VALUES
(1, '1020304050', 'Daniel Muñoz (Admin/Dev)', 'ACTIVO'),
(2, '9876543210', 'Carlos Gómez (Cajero POS 1)', 'ACTIVO'),
(3, '1122334455', 'Laura Restrepo (Ventas POS 2)', 'ACTIVO');

-- Programación para la fecha actual (CURDATE()) ejemplo 
-- Turno Daniel: 08:00 AM (Con almuerzo 60 min)
INSERT INTO programacion_semanal (id_empleado, fecha, hora_entrada_programada, minutos_almuerzo_programados, horas_extras_autorizadas, tipo_novedad_precargada) VALUES
(1, CURDATE(), '08:00:00', 60, TRUE, 'NINGUNA'),
(2, CURDATE(), '07:00:00', 0, FALSE, 'NINGUNA'),
(3, CURDATE(), '09:00:00', 60, FALSE, 'NINGUNA');
