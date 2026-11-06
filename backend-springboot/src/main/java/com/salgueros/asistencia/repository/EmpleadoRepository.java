package com.salgueros.asistencia.repository;

import com.salgueros.asistencia.model.Empleado;
import com.salgueros.asistencia.model.EstadoEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Integer> {
    Optional<Empleado> findByCedula(String cedula);
    List<Empleado> findByEstado(EstadoEmpleado estado);
    List<Empleado> findByEstadoAndArea(EstadoEmpleado estado, String area);
}
