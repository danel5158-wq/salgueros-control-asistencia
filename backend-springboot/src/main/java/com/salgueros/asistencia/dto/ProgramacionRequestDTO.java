package com.salgueros.asistencia.dto;

import com.salgueros.asistencia.model.TipoNovedad;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramacionRequestDTO {
    
    //  PIN de seguridad administrativa para evitar que los cajeros
    
    private String adminPin;

    // no puede venir solo o vacio por que se aplica a todos 

    private Integer idEmpleado;
    private String area; // null/TODOS, PLANTA, VENTAS, TRANSPORTADORES, ADMINISTRADORES

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    private LocalTime horaEntrada;
    private Integer minutosAlmuerzo;
    private Boolean horasExtras;
    private TipoNovedad tipoNovedad;
}
