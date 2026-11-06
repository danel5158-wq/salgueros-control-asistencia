package com.salgueros.asistencia.repository;

import com.salgueros.asistencia.model.ProgramacionSemanal;
import com.salgueros.asistencia.model.RegistroAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroAsistenciaRepository extends JpaRepository<RegistroAsistencia, Long> {
    Optional<RegistroAsistencia> findByProgramacion(ProgramacionSemanal programacion);

    @Query("SELECT r FROM RegistroAsistencia r " +
           "JOIN FETCH r.programacion p " +
           "JOIN FETCH p.empleado e " +
           "WHERE p.fecha BETWEEN :inicio AND :fin " +
           "ORDER BY p.fecha DESC, e.nombreApellido ASC")
    List<RegistroAsistencia> findRegistrosEntreFechas(@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin);
}
