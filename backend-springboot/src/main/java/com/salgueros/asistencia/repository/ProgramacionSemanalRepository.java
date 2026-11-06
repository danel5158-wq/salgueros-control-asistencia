package com.salgueros.asistencia.repository;

import com.salgueros.asistencia.model.Empleado;
import com.salgueros.asistencia.model.ProgramacionSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramacionSemanalRepository extends JpaRepository<ProgramacionSemanal, Long> {
    Optional<ProgramacionSemanal> findByEmpleadoAndFecha(Empleado empleado, LocalDate fecha);
    List<ProgramacionSemanal> findByFechaBetween(LocalDate inicio, LocalDate fin);
}
