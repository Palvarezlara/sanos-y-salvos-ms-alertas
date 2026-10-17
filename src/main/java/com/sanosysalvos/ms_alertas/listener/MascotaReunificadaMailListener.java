package com.sanosysalvos.ms_alertas.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.MascotaReunificadaEvento;
import com.sanosysalvos.ms_alertas.messaging.SqsPollingConsumer;

import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Consume cola-mascota-reunificada: avisa por correo a quien creó el
 * reporte que se cerró (dueño si era EXTRAVIADO, quien la encontró si era
 * ENCONTRADO) — cada reporte se notifica a sí mismo, nunca se cruza con un
 * segundo reporte de la misma mascota.
 */
@Component
public class MascotaReunificadaMailListener extends SqsPollingConsumer {

    private static final Logger log = LoggerFactory.getLogger(MascotaReunificadaMailListener.class);

    private final JavaMailSender mailSender;
    private final String remitente;
    private final ObjectMapper objectMapper;

    public MascotaReunificadaMailListener(
            SqsClient sqsClient,
            @Value("${sqs.cola-mascota-reunificada-url}") String queueUrl,
            @Value("${sqs.consumidor.auto-arranque:true}") boolean autoArranque,
            JavaMailSender mailSender,
            @Value("${contacto.remitente}") String remitente,
            ObjectMapper objectMapper) {
        super(sqsClient, queueUrl, autoArranque);
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void procesar(String cuerpoMensaje) throws Exception {
        MascotaReunificadaEvento evento = objectMapper.readValue(cuerpoMensaje, MascotaReunificadaEvento.class);
        enviarCorreo(evento);
    }

    public void enviarCorreo(MascotaReunificadaEvento evento) {
        SimpleMailMessage correo = new SimpleMailMessage();
        correo.setFrom(remitente);
        correo.setTo(evento.getEmailContacto());
        correo.setSubject("¡" + evento.getNombreMascota() + " quedó marcada como reunificada! — Sanos y Salvos");
        correo.setText(construirCuerpo(evento));

        mailSender.send(correo);

        log.info("Correo de reunificacion enviado para mascotaId={}", evento.getMascotaId());
    }

    private String construirCuerpo(MascotaReunificadaEvento evento) {
        return "Hola,\n\n"
                + "Tu reporte de " + evento.getNombreMascota() + " en Sanos y Salvos quedó marcado como reunificado.\n\n"
                + "Gracias por usar la plataforma y por ayudar a que las mascotas vuelvan a casa.\n\n"
                + "— Sanos y Salvos";
    }
}
