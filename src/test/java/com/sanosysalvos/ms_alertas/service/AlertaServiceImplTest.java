package com.sanosysalvos.ms_alertas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanosysalvos.ms_alertas.dto.ActualizarAlertaDto;
import com.sanosysalvos.ms_alertas.dto.AlertaRequestDto;
import com.sanosysalvos.ms_alertas.dto.AlertaResponseDto;
import com.sanosysalvos.ms_alertas.exception.ResourceNotFoundException;
import com.sanosysalvos.ms_alertas.model.Alerta;
import com.sanosysalvos.ms_alertas.model.EstadoAlerta;
import com.sanosysalvos.ms_alertas.repository.AlertaRepository;
import com.sanosysalvos.ms_alertas.service.impl.AlertaServiceImpl;

@ExtendWith(MockitoExtension.class)
class AlertaServiceImplTest {

    @Mock
    private AlertaRepository alertaRepository;

    private AlertaService alertaService;

    private AlertaRequestDto crearRequest() {
        AlertaRequestDto dto = new AlertaRequestDto();
        dto.setMascotaId("64f1a2b3c4d5e6f7a8b9c0d1");
        dto.setLatitudCentro(-33.45);
        dto.setLongitudCentro(-70.66);
        dto.setRadioKm(3.0);
        return dto;
    }

    private Alerta alertaGuardada() {
        return Alerta.builder()
                .id(1L)
                .mascotaId("64f1a2b3c4d5e6f7a8b9c0d1")
                .latitudCentro(-33.45)
                .longitudCentro(-70.66)
                .radioKm(3.0)
                .estado(EstadoAlerta.ACTIVA)
                .build();
    }

    @BeforeEach
    void setUp() {
        alertaService = new AlertaServiceImpl(alertaRepository);
    }

    @Test
    void crearAlerta_guarda_como_activa_y_devuelve_el_dto() {
        AlertaRequestDto request = crearRequest();
        ArgumentCaptor<Alerta> captor = ArgumentCaptor.forClass(Alerta.class);
        when(alertaRepository.save(captor.capture())).thenReturn(alertaGuardada());

        AlertaResponseDto respuesta = alertaService.crearAlerta(request);

        Alerta guardada = captor.getValue();
        assertThat(guardada.getEstado()).isEqualTo(EstadoAlerta.ACTIVA);
        assertThat(guardada.getCreadaEn()).isNotNull();
        assertThat(guardada.getMascotaId()).isEqualTo(request.getMascotaId());
        assertThat(respuesta.getId()).isEqualTo(1L);
        assertThat(respuesta.getEstado()).isEqualTo(EstadoAlerta.ACTIVA);
    }

    @Test
    void buscarPorZona_delega_en_el_repositorio_y_mapea_resultados() {
        when(alertaRepository.buscarPorZona(-33.45, -70.66, 3.0)).thenReturn(List.of(alertaGuardada()));

        List<AlertaResponseDto> resultado = alertaService.buscarPorZona(-33.45, -70.66, 3.0);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getMascotaId()).isEqualTo("64f1a2b3c4d5e6f7a8b9c0d1");
    }

    @Test
    void actualizarEstado_lanza_notFound_si_no_existe() {
        when(alertaRepository.findById(99L)).thenReturn(Optional.empty());
        ActualizarAlertaDto dto = new ActualizarAlertaDto();
        dto.setEstado(EstadoAlerta.INACTIVA);

        assertThatThrownBy(() -> alertaService.actualizarEstado(99L, dto))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(alertaRepository, never()).save(any(Alerta.class));
    }

    @Test
    void eliminarAlerta_lanza_notFound_si_no_existe() {
        when(alertaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> alertaService.eliminarAlerta(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(alertaRepository, never()).deleteById(any());
    }
}
