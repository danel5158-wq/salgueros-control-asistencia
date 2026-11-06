package com.salgueros.asistencia.repository;

import com.salgueros.asistencia.model.Empleado;
import com.salgueros.asistencia.model.VectorFacial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VectorFacialRepository extends JpaRepository<VectorFacial, Integer> {
    List<VectorFacial> findByEmpleado(Empleado empleado);

    @Query("SELECT v FROM VectorFacial v JOIN FETCH v.empleado e WHERE e.estado = 'ACTIVO'")
    List<VectorFacial> findAllActiveEmbeddings();
}
