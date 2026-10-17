package com.sanosysalvos.ms_alertas.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.AlertaRequestDto;
import com.sanosysalvos.ms_alertas.dto.MascotaReportadaEvento;
import com.sanosysalvos.ms_alertas.service.AlertaService;

import software.amazon.awssdk.services.sqs.SqsClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MascotaReportadaListenerTest {

    @Mock
    private AlertaService alertaService;

    @Mock
    private SqsClient sqsClient;

    private MascotaReportadaListener listener;

    @BeforeEach
    void setUp() {
        listener = new MascotaReportadaListener(sqsClient, "http://dummy/cola-mascota-reportada", false,
                alertaService, new ObjectMapper());
    }

    @Test
    void crearAlerta_arma_el_dto_con_radio_de_3km_por_defecto() {
        MascotaReportadaEvento evento = new MascotaReportadaEvento("mascota-1", -33.456789, -70.987654);

        listener.crearAlerta(evento);

        ArgumentCaptor<AlertaRequestDto> captor = ArgumentCaptor.forClass(AlertaRequestDto.class);
        verify(alertaService).crearAlerta(captor.capture());

        AlertaRequestDto dto = captor.getValue();
        assertThat(dto.getMascotaId()).isEqualTo("mascota-1");
        assertThat(dto.getLatitudCentro()).isEqualTo(-33.456789);
        assertThat(dto.getLongitudCentro()).isEqualTo(-70.987654);
        assertThat(dto.getRadioKm()).isEqualTo(3.0);
    }

    @Test
    void procesar_deserializa_el_json_de_sqs_y_crea_la_alerta() throws Exception {
        String cuerpoMensaje = "{\"mascotaId\":\"mascota-2\",\"latitud\":-33.03,\"longitud\":-71.55}";

        var procesarMetodo = MascotaReportadaListener.class.getDeclaredMethod("procesar", String.class);
        procesarMetodo.setAccessible(true);
        procesarMetodo.invoke(listener, cuerpoMensaje);

        ArgumentCaptor<AlertaRequestDto> captor = ArgumentCaptor.forClass(AlertaRequestDto.class);
        verify(alertaService).crearAlerta(captor.capture());
        assertThat(captor.getValue().getMascotaId()).isEqualTo("mascota-2");
    }
}
