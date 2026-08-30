package com.sanosysalvos.ms_alertas.dto;

import java.time.LocalDateTime;

import com.sanosysalvos.ms_alertas.model.EstadoAlerta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaResponseDto {
    private Long id;
    private String mascotaId;
    private Double latitudCentro;
    private Double longitudCentro;
    private Double radioKm;
    private EstadoAlerta estado;
    private LocalDateTime creadaEn;
}
