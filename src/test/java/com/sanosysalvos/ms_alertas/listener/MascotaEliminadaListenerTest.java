package com.sanosysalvos.ms_alertas.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.service.AlertaService;

import software.amazon.awssdk.services.sqs.SqsClient;

@ExtendWith(MockitoExtension.class)
class MascotaEliminadaListenerTest {

    @Mock
    private AlertaService alertaService;

    @Mock
    private SqsClient sqsClient;

    private MascotaEliminadaListener listener;

    @BeforeEach
    void setUp() {
        listener = new MascotaEliminadaListener(sqsClient, "http://dummy/cola-mascota-eliminada", false,
                alertaService, new ObjectMapper());
    }

    @Test
    void desactiva_las_alertas_del_reporte_eliminado() throws Exception {
        listener.procesar("{\"mascotaId\":\"mascota-7\"}");

        verify(alertaService).desactivarPorMascota("mascota-7", "ELIMINADA");
    }

    @Test
    void descarta_un_evento_sin_mascotaId_en_vez_de_reintentarlo() throws Exception {
        listener.procesar("{}");
        listener.procesar("{\"mascotaId\":\"  \"}");

        verify(alertaService, never()).desactivarPorMascota(any(), any());
    }
}
