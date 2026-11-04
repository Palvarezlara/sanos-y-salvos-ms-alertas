package com.sanosysalvos.ms_alertas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Marca de un reporte que ya no debe generar alertas (reunificado o eliminado).
 *
 * Las colas son independientes, así que "mascota.reunificada" puede llegar antes que "mascota.reportada": sin
 * esta marca la desactivación no encuentra nada y la alerta nacería ACTIVA para siempre.
 */
@Entity
@Table(name = "mascotas_cerradas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaCerrada {

    @Id
    @Column(name = "mascota_id", nullable = false)
    private String mascotaId;

    @Column(nullable = false, length = 30)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime cerradaEn;
}
