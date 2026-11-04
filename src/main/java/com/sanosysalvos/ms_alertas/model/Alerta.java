package com.sanosysalvos.ms_alertas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
// Los indices cubren findByMascotaIdAndEstado (en cada evento) y el prefiltro de buscarPorZona (publico).
// uk_alertas_activa_por_mascota impide dos alertas ACTIVAS del mismo reporte aunque dos instancias (o una
// reentrega de SQS) intenten crearla a la vez: clave_activa vale el mascotaId solo si esta ACTIVA, y
// MySQL permite varios NULL en un indice unico.
@Table(name = "alertas",
        indexes = {
                @Index(name = "idx_alertas_mascota_estado", columnList = "mascota_id,estado"),
                @Index(name = "idx_alertas_estado_zona", columnList = "estado,latitud_centro,longitud_centro")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_alertas_activa_por_mascota", columnNames = "clave_activa"))
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

    @Column(name = "clave_activa")
    private String claveActiva;

    @PrePersist
    @PreUpdate
    void calcularClaveActiva() {
        this.claveActiva = estado == EstadoAlerta.ACTIVA ? mascotaId : null;
    }
}
