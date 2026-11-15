package com.sanosysalvos.ms_alertas.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espejo de com.duoc.ms_mascotas.DTO.MascotaEliminadaEvento: ms-mascotas lo publica cuando se borra un reporte
 * (el dueño o un administrador) para que no queden alertas huérfanas (Ley 21.719).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaEliminadaEvento implements Serializable {
    private String mascotaId;
}
