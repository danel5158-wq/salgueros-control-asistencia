package com.salgueros.asistencia.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarcacionRequestDTO {
    /**
     * Vector biométrico de 128 flotantes 
     */
    private List<Double> vectorEmbedding;

    /**
     * Tipo: ENTRADA, SALIDA_ALMUERZO, REGRESO_ALMUERZO, SALIDA
     */
    private String tipoMarcacion;

    /**
     * NO TOCAR
     */
    private Boolean forzarConfirmacion;
}
