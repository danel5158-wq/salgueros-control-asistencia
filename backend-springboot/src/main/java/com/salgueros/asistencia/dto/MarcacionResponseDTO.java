package com.salgueros.asistencia.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcacionResponseDTO {
    private boolean exito;
    private String mensaje;
    private String nombreEmpleado;
    private String cedula;
    private String tipoMarcacion;
    private String estadoLlegada;
    private int minutosRetardo;
    private String horaMarcacion;
    private double distanciaBiometrica;
    private boolean requiereConfirmacionDobleMarcacion;
}
