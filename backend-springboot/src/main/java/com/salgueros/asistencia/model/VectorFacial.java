package com.salgueros.asistencia.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "vectores_faciales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorFacial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_vector")
    private Integer idVector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_empleado", nullable = false)
    private Empleado empleado;

    
    @Column(name = "vector_embedding", columnDefinition = "TEXT", nullable = false)
    private String vectorEmbedding;
}
