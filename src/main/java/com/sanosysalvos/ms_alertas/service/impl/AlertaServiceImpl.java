package com.sanosysalvos.ms_alertas.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sanosysalvos.ms_alertas.dto.ActualizarAlertaDto;
import com.sanosysalvos.ms_alertas.dto.AlertaRequestDto;
import com.sanosysalvos.ms_alertas.dto.AlertaResponseDto;
import com.sanosysalvos.ms_alertas.exception.ResourceNotFoundException;
import com.sanosysalvos.ms_alertas.model.Alerta;
import com.sanosysalvos.ms_alertas.model.EstadoAlerta;
import com.sanosysalvos.ms_alertas.repository.AlertaRepository;
import com.sanosysalvos.ms_alertas.service.AlertaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertaServiceImpl implements AlertaService {

    private final AlertaRepository alertaRepository;

    @Override
    public AlertaResponseDto crearAlerta(AlertaRequestDto dto) {
        Alerta alerta = Alerta.builder()
                .mascotaId(dto.getMascotaId())
                .latitudCentro(dto.getLatitudCentro())
                .longitudCentro(dto.getLongitudCentro())
                .radioKm(dto.getRadioKm())
                .estado(EstadoAlerta.ACTIVA)
                .creadaEn(LocalDateTime.now())
                .build();

        Alerta guardada = alertaRepository.save(alerta);
        log.info("Alerta creada: id={}, mascotaId={}", guardada.getId(), guardada.getMascotaId());
        return toResponseDto(guardada);
    }

    @Override
    public List<AlertaResponseDto> listarActivas() {
        return alertaRepository.findByEstado(EstadoAlerta.ACTIVA).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Override
    public List<AlertaResponseDto> buscarPorZona(Double lat, Double lng, Double radioKm) {
        return alertaRepository.buscarPorZona(lat, lng, radioKm).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Override
    public AlertaResponseDto actualizarEstado(Long id, ActualizarAlertaDto dto) {
        Alerta alerta = alertaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta no encontrada: " + id));

        alerta.setEstado(dto.getEstado());
        Alerta actualizada = alertaRepository.save(alerta);
        log.info("Alerta actualizada: id={}, estado={}", id, dto.getEstado());
        return toResponseDto(actualizada);
    }

    @Override
    public void eliminarAlerta(Long id) {
        if (!alertaRepository.existsById(id)) {
            throw new ResourceNotFoundException("Alerta no encontrada: " + id);
        }
        alertaRepository.deleteById(id);
        log.info("Alerta eliminada: id={}", id);
    }

    private AlertaResponseDto toResponseDto(Alerta alerta) {
        return AlertaResponseDto.builder()
                .id(alerta.getId())
                .mascotaId(alerta.getMascotaId())
                .latitudCentro(alerta.getLatitudCentro())
                .longitudCentro(alerta.getLongitudCentro())
                .radioKm(alerta.getRadioKm())
                .estado(alerta.getEstado())
                .creadaEn(alerta.getCreadaEn())
                .build();
    }
}
