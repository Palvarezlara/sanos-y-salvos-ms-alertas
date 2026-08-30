package com.sanosysalvos.ms_alertas.dto;

import com.sanosysalvos.ms_alertas.model.EstadoAlerta;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActualizarAlertaDto {

    @NotNull(message = "estado es requerido")
    private EstadoAlerta estado;
}
