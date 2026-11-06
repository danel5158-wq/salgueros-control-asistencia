package com.salgueros.asistencia.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "empleados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado")
    private Integer idEmpleado;

    @Column(name = "cedula", nullable = false, unique = true, length = 20)
    private String cedula;

    @Column(name = "nombre_apellido", nullable = false, length = 100)
    private String nombreApellido;

    @Column(name = "area", nullable = false, length = 50)
    @Builder.Default
    private String area = "PLANTA";

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoEmpleado estado = EstadoEmpleado.ACTIVO;
}
