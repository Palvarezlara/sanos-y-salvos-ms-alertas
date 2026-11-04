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
import com.sanosysalvos.ms_alertas.model.MascotaCerrada;
import com.sanosysalvos.ms_alertas.repository.AlertaRepository;
import com.sanosysalvos.ms_alertas.repository.MascotaCerradaRepository;
import com.sanosysalvos.ms_alertas.service.impl.AlertaServiceImpl;

@ExtendWith(MockitoExtension.class)
class AlertaServiceImplTest {

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private MascotaCerradaRepository mascotaCerradaRepository;

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
        alertaService = new AlertaServiceImpl(alertaRepository, mascotaCerradaRepository);
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

    @Test
    void desactivarPorMascota_pasa_a_inactiva_solo_las_alertas_activas_de_esa_mascota() {
        Alerta activa = alertaGuardada();
        when(alertaRepository.findByMascotaIdAndEstado("64f1a2b3c4d5e6f7a8b9c0d1", EstadoAlerta.ACTIVA))
                .thenReturn(List.of(activa));

        alertaService.desactivarPorMascota("64f1a2b3c4d5e6f7a8b9c0d1", "REUNIFICADA");

        assertThat(activa.getEstado()).isEqualTo(EstadoAlerta.INACTIVA);
        verify(alertaRepository).saveAll(List.of(activa));
    }

    @Test
    void desactivarPorMascota_deja_la_marca_aunque_la_alerta_todavia_no_exista() {
        // mascota.reunificada llego antes que mascota.reportada: sin la marca, la alerta nacia activa para siempre
        when(alertaRepository.findByMascotaIdAndEstado("m-1", EstadoAlerta.ACTIVA)).thenReturn(List.of());
        when(mascotaCerradaRepository.existsById("m-1")).thenReturn(false);

        alertaService.desactivarPorMascota("m-1", "REUNIFICADA");

        ArgumentCaptor<MascotaCerrada> captor = ArgumentCaptor.forClass(MascotaCerrada.class);
        verify(mascotaCerradaRepository).save(captor.capture());
        assertThat(captor.getValue().getMascotaId()).isEqualTo("m-1");
        assertThat(captor.getValue().getMotivo()).isEqualTo("REUNIFICADA");
        assertThat(captor.getValue().getCerradaEn()).isNotNull();
    }

    @Test
    void desactivarPorMascota_no_repite_la_marca_si_el_evento_llega_dos_veces() {
        when(alertaRepository.findByMascotaIdAndEstado("m-1", EstadoAlerta.ACTIVA)).thenReturn(List.of());
        when(mascotaCerradaRepository.existsById("m-1")).thenReturn(true);

        alertaService.desactivarPorMascota("m-1", "ELIMINADA");

        verify(mascotaCerradaRepository, never()).save(any(MascotaCerrada.class));
    }

    @Test
    void mascotaCerrada_consulta_la_marca() {
        when(mascotaCerradaRepository.existsById("m-1")).thenReturn(true);
        when(mascotaCerradaRepository.existsById("m-2")).thenReturn(false);

        assertThat(alertaService.mascotaCerrada("m-1")).isTrue();
        assertThat(alertaService.mascotaCerrada("m-2")).isFalse();
    }

    @Test
    void buscarPorZona_devuelve_coordenadas_aproximadas_no_las_exactas() {
        Alerta exacta = alertaGuardada();
        exacta.setLatitudCentro(-33.456789);
        exacta.setLongitudCentro(-70.663214);
        when(alertaRepository.buscarPorZona(-33.45, -70.66, 2.0)).thenReturn(List.of(exacta));

        AlertaResponseDto respuesta = alertaService.buscarPorZona(-33.45, -70.66, 2.0).get(0);

        // ubicacion aproximada por privacidad (CLAUDE.md): 2 decimales, ~1,1 km
        assertThat(respuesta.getLatitudCentro()).isEqualTo(-33.46);
        assertThat(respuesta.getLongitudCentro()).isEqualTo(-70.66);
    }

    @Test
    void existeActivaParaMascota_refleja_si_hay_alertas_activas() {
        when(alertaRepository.findByMascotaIdAndEstado("m1", EstadoAlerta.ACTIVA)).thenReturn(List.of(alertaGuardada()));
        when(alertaRepository.findByMascotaIdAndEstado("m2", EstadoAlerta.ACTIVA)).thenReturn(List.of());

        assertThat(alertaService.existeActivaParaMascota("m1")).isTrue();
        assertThat(alertaService.existeActivaParaMascota("m2")).isFalse();
    }
}
