package com.sanosysalvos.ms_alertas.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.sanosysalvos.ms_alertas.config.RabbitMQConfig;
import com.sanosysalvos.ms_alertas.dto.AlertaRequestDto;
import com.sanosysalvos.ms_alertas.dto.MascotaReportadaEvento;
import com.sanosysalvos.ms_alertas.service.AlertaService;

/**
 * Consume cola.mascota.reportada y crea la Alerta geográfica correspondiente
 * (reusa AlertaService.crearAlerta, la misma lógica que ya usa el endpoint
 * manual POST /alertas).
 *
 * Antes de esto, GET /alertas/zona siempre devolvía vacío: nada llamaba a
 * crearAlerta en el flujo real de la app.
 */
@Component
public class MascotaReportadaListener {

    private static final Logger log = LoggerFactory.getLogger(MascotaReportadaListener.class);

    // Recomendado por el diseño original del proyecto; no viaja en el evento
    // porque ms-mascotas no tiene (ni debería tener) ningún concepto de radio.
    private static final double RADIO_KM_DEFECTO = 3.0;

    private final AlertaService alertaService;

    public MascotaReportadaListener(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @RabbitListener(queues = RabbitMQConfig.COLA_MASCOTA_REPORTADA)
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
