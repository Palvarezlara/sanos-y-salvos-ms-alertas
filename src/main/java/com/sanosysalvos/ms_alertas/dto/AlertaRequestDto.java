package com.sanosysalvos.ms_alertas.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertaRequestDto {

    @NotBlank(message = "mascotaId es requerido")
    private String mascotaId;

    @NotNull(message = "latitudCentro es requerida")
    @DecimalMin(value = "-90.0", message = "latitudCentro fuera de rango")
    @DecimalMax(value = "90.0", message = "latitudCentro fuera de rango")
    private Double latitudCentro;

    @NotNull(message = "longitudCentro es requerida")
    @DecimalMin(value = "-180.0", message = "longitudCentro fuera de rango")
    @DecimalMax(value = "180.0", message = "longitudCentro fuera de rango")
    private Double longitudCentro;

    @NotNull(message = "radioKm es requerido")
    @DecimalMin(value = "0.1", message = "radioKm debe ser mayor a 0")
    private Double radioKm;
}
