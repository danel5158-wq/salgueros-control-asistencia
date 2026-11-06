package com.salgueros.asistencia.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearEmpleadoRequestDTO {
    private String adminPin;
    private String cedula;
    private String nombreApellido;
    private String area;
}
