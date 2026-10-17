package com.sanosysalvos.ms_alertas.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espejo de com.duoc.ms_mascotas.DTO.MascotaReunificadaEvento — publicado
 * por ms-mascotas cuando un reporte pasa a REUNIFICADO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaReunificadaEvento implements Serializable {
    private String mascotaId;
    private String nombreMascota;
    private String emailContacto;
}
