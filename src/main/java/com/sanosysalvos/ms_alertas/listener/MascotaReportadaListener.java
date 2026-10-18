package com.sanosysalvos.ms_alertas.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.AlertaRequestDto;
import com.sanosysalvos.ms_alertas.dto.MascotaReportadaEvento;
import com.sanosysalvos.ms_alertas.messaging.SqsPollingConsumer;
import com.sanosysalvos.ms_alertas.service.AlertaService;

import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Consume cola-mascota-reportada y crea la Alerta geográfica correspondiente
 * (reusa AlertaService.crearAlerta, la misma lógica que ya usa el endpoint
 * manual POST /alertas).
 *
 * Antes de esto, GET /alertas/zona siempre devolvía vacío: nada llamaba a
 * crearAlerta en el flujo real de la app.
 */
@Component
public class MascotaReportadaListener extends SqsPollingConsumer {

    private static final Logger log = LoggerFactory.getLogger(MascotaReportadaListener.class);

    // Feedback del equipo (18-oct-2026): 3km traía resultados de ciudades
    // vecinas, se achicó a 1.5km. No viaja en el evento porque ms-mascotas
    // no tiene (ni debería tener) ningún concepto de radio.
    private static final double RADIO_KM_DEFECTO = 1.5;

    private final AlertaService alertaService;
    private final ObjectMapper objectMapper;

    public MascotaReportadaListener(
            SqsClient sqsClient,
            @Value("${sqs.cola-mascota-reportada-url}") String queueUrl,
            @Value("${sqs.consumidor.auto-arranque:true}") boolean autoArranque,
            AlertaService alertaService,
            ObjectMapper objectMapper) {
        super(sqsClient, queueUrl, autoArranque);
        this.alertaService = alertaService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void procesar(String cuerpoMensaje) throws Exception {
        MascotaReportadaEvento evento = objectMapper.readValue(cuerpoMensaje, MascotaReportadaEvento.class);
        crearAlerta(evento);
    }

    public void crearAlerta(MascotaReportadaEvento evento) {
        AlertaRequestDto dto = new AlertaRequestDto();
        dto.setMascotaId(evento.getMascotaId());
        dto.setLatitudCentro(evento.getLatitud());
        dto.setLongitudCentro(evento.getLongitud());
        dto.setRadioKm(RADIO_KM_DEFECTO);

        alertaService.crearAlerta(dto);
        log.info("Alerta creada automaticamente a partir de mascota.reportada, mascotaId={}", evento.getMascotaId());
    }
}
