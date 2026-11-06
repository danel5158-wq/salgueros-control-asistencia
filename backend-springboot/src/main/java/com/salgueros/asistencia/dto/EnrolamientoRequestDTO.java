package com.salgueros.asistencia.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrolamientoRequestDTO {
    private String cedula;
    private List<Double> vectorEmbedding;
}
