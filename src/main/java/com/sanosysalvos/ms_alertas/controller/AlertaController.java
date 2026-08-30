package com.sanosysalvos.ms_alertas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sanosysalvos.ms_alertas.dto.ActualizarAlertaDto;
import com.sanosysalvos.ms_alertas.dto.AlertaRequestDto;
import com.sanosysalvos.ms_alertas.dto.AlertaResponseDto;
import com.sanosysalvos.ms_alertas.service.AlertaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/alertas")
@RequiredArgsConstructor
public class AlertaController {

    private final AlertaService alertaService;

    @PostMapping
    public ResponseEntity<AlertaResponseDto> crearAlerta(@Valid @RequestBody AlertaRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertaService.crearAlerta(dto));
    }

    @GetMapping
    public List<AlertaResponseDto> listarActivas() {
        return alertaService.listarActivas();
    }

    @GetMapping("/zona")
    public List<AlertaResponseDto> buscarPorZona(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam Double radioKm) {
        return alertaService.buscarPorZona(lat, lng, radioKm);
    }

    @PatchMapping("/{id}")
    public AlertaResponseDto actualizarEstado(@PathVariable Long id, @Valid @RequestBody ActualizarAlertaDto dto) {
        return alertaService.actualizarEstado(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarAlerta(@PathVariable Long id) {
        alertaService.eliminarAlerta(id);
        return ResponseEntity.noContent().build();
    }
}
