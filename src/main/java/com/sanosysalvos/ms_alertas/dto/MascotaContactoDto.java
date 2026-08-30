package com.sanosysalvos.ms_alertas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta del endpoint interno GET /internal/mascotas/{id}/contacto de ms-mascotas.
 * Nunca se expone directamente al frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaContactoDto {
    private String mascotaId;
    private String emailDestino;
    private String nombreMascota;
}
