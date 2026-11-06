package com.salgueros.asistencia.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "programacion_semanal", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"id_empleado", "fecha"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramacionSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_programacion")
    private Long idProgramacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_entrada_programada", nullable = false)
    private LocalTime horaEntradaProgramada;

    
    @Column(name = "minutos_almuerzo_programados", nullable = false)
    @Builder.Default
    private Integer minutosAlmuerzoProgramados = 60;

    @Column(name = "horas_extras_autorizadas", nullable = false)
    @Builder.Default
    private Boolean horasExtrasAutorizadas = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_novedad_precargada", nullable = false)
    @Builder.Default
    private TipoNovedad tipoNovedadPrecargada = TipoNovedad.NINGUNA;
}
