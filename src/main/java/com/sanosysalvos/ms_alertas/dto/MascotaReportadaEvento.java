package com.sanosysalvos.ms_alertas.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que publica ms-mascotas en cola.mascota.reportada. Mismo nombre de
 * campos en ambos lados a propósito, para que el JSON se mapee sin
 * configuración extra.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaReportadaEvento implements Serializable {
    private String mascotaId;
    private Double latitud;
    private Double longitud;
}
