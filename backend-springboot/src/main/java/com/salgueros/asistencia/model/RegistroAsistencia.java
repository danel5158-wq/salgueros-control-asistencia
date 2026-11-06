package com.salgueros.asistencia.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registros_asistencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Long idRegistro;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programacion", nullable = false, unique = true)
    private ProgramacionSemanal programacion;

    @Column(name = "hora_entrada_real", nullable = false)
    private LocalDateTime horaEntradaReal;

    @Column(name = "hora_salida_almuerzo")
    private LocalDateTime horaSalidaAlmuerzo;

    @Column(name = "hora_regreso_almuerzo")
    private LocalDateTime horaRegresoAlmuerzo;

    @Column(name = "hora_salida_real")
    private LocalDateTime horaSalidaReal;

    @Column(name = "minutos_retardo", nullable = false)
    @Builder.Default
    private Integer minutosRetardo = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_llegada", nullable = false)
    private EstadoLlegada estadoLlegada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_dia", nullable = false)
    @Builder.Default
    private EstadoDia estadoDia = EstadoDia.EN_CURSO;
}
