package com.sanosysalvos.ms_alertas.service;

import java.util.List;

import com.sanosysalvos.ms_alertas.dto.ActualizarAlertaDto;
import com.sanosysalvos.ms_alertas.dto.AlertaRequestDto;
import com.sanosysalvos.ms_alertas.dto.AlertaResponseDto;

public interface AlertaService {

    AlertaResponseDto crearAlerta(AlertaRequestDto dto);

    List<AlertaResponseDto> listarActivas();

    List<AlertaResponseDto> buscarPorZona(Double lat, Double lng, Double radioKm);

    AlertaResponseDto actualizarEstado(Long id, ActualizarAlertaDto dto);

    void eliminarAlerta(Long id);
}
