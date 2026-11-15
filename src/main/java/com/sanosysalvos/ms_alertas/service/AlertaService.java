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

    // Se llama al consumir mascota.reunificada o mascota.eliminada: un reporte cerrado no
    // debe seguir generando avisos de zona (ni en el mapa ni en el aviso de proximidad del
    // frontend). Además deja una marca para que un mascota.reportada que llegue después
    // (las colas son independientes) no cree una alerta que nadie desactivaría.
    void desactivarPorMascota(String mascotaId, String motivo);

    boolean mascotaCerrada(String mascotaId);

    boolean existeActivaParaMascota(String mascotaId);
}
