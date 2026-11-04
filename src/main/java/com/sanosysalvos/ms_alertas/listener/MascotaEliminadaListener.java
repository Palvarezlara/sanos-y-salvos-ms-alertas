package com.sanosysalvos.ms_alertas.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.MascotaEliminadaEvento;
import com.sanosysalvos.ms_alertas.messaging.SqsPollingConsumer;
import com.sanosysalvos.ms_alertas.service.AlertaService;

import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Consume cola-mascota-eliminada: cuando se borra un reporte en ms-mascotas, sus alertas de zona dejan de
 * mostrarse y se evita que una alerta tardía lo reviva.
 *
 * La URL de la cola es opcional: si Terraform aún no la define, este consumidor simplemente no arranca.
 */
@Component
public class MascotaEliminadaListener extends SqsPollingConsumer {

    private static final Logger log = LoggerFactory.getLogger(MascotaEliminadaListener.class);

    private final AlertaService alertaService;
    private final ObjectMapper objectMapper;

    public MascotaEliminadaListener(
            SqsClient sqsClient,
            @Value("${sqs.cola-mascota-eliminada-url:}") String queueUrl,
            @Value("${sqs.consumidor.auto-arranque:true}") boolean autoArranque,
            AlertaService alertaService,
            ObjectMapper objectMapper) {
        super(sqsClient, queueUrl, autoArranque);
        this.alertaService = alertaService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void procesar(String cuerpoMensaje) throws Exception {
        MascotaEliminadaEvento evento = objectMapper.readValue(cuerpoMensaje, MascotaEliminadaEvento.class);
        if (evento.getMascotaId() == null || evento.getMascotaId().isBlank()) {
            /* evento mal formado: reintentarlo no lo arregla; se descarta */
            log.warn("Evento mascota.eliminada descartado: sin mascotaId");
            return;
        }
        alertaService.desactivarPorMascota(evento.getMascotaId(), "ELIMINADA");
    }
}
