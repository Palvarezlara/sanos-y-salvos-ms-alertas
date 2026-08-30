package com.sanosysalvos.ms_alertas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "alertas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ObjectId de Mongo (ms-mascotas), no Long — ver CLAUDE.md.
    @Column(nullable = false)
    private String mascotaId;

    @Column(nullable = false)
    private Double latitudCentro;

    @Column(nullable = false)
    private Double longitudCentro;

    @Column(nullable = false)
    private Double radioKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAlerta estado;

    @Column(nullable = false)
    private LocalDateTime creadaEn;
}
