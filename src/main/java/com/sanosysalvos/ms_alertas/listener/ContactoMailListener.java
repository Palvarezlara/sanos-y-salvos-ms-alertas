package com.sanosysalvos.ms_alertas.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.ContactoMensajeDto;
import com.sanosysalvos.ms_alertas.messaging.SqsPollingConsumer;

import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Consume cola-contacto y recién acá llama a JavaMailSender — antes esto
 * corría en el mismo hilo que la petición del usuario (ContactoServiceImpl),
 * bloqueándola mientras SMTP respondía.
 *
 * Si enviarCorreo() lanza una excepción, el mensaje no se borra de la cola;
 * SQS lo reintenta y, agotados los intentos, lo manda solo a
 * cola-contacto-fallidos (redrive policy, ver terraform/mensajeria.tf).
 */
@Component
public class ContactoMailListener extends SqsPollingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContactoMailListener.class);

    private final JavaMailSender mailSender;
    private final String remitente;
    private final ObjectMapper objectMapper;

    public ContactoMailListener(
            SqsClient sqsClient,
            @Value("${sqs.cola-contacto-url}") String queueUrl,
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
        ContactoMensajeDto mensaje = objectMapper.readValue(cuerpoMensaje, ContactoMensajeDto.class);
        enviarCorreo(mensaje);
    }

    public void enviarCorreo(ContactoMensajeDto mensaje) {
        SimpleMailMessage correo = new SimpleMailMessage();
        correo.setFrom(remitente);
        correo.setTo(mensaje.getEmailDestino());
        correo.setReplyTo(mensaje.getEmailContacto());
        correo.setSubject("Alguien tiene información sobre " + mensaje.getNombreMascota() + " — Sanos y Salvos");
        correo.setText(construirCuerpo(mensaje));

        mailSender.send(correo);

        // No se registra el correo ni el contenido del mensaje en el log.
        log.info("Correo de contacto enviado para mascotaId={}", mensaje.getMascotaId());
    }

    private String construirCuerpo(ContactoMensajeDto mensaje) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("Hola,\n\n");
        cuerpo.append(mensaje.getNombreContacto())
                .append(" dejó este mensaje sobre ")
                .append(mensaje.getNombreMascota())
                .append(" en Sanos y Salvos:\n\n");
        cuerpo.append('"').append(mensaje.getMensaje()).append('"').append("\n\n");
        cuerpo.append("Correo de contacto: ").append(mensaje.getEmailContacto()).append('\n');
        if (mensaje.getTelefonoContacto() != null && !mensaje.getTelefonoContacto().isBlank()) {
            cuerpo.append("Teléfono: ").append(mensaje.getTelefonoContacto()).append('\n');
        }
        cuerpo.append("\nPuedes responder directamente a este correo para contactar a esta persona.\n\n");
        cuerpo.append("— Sanos y Salvos");
        return cuerpo.toString();
    }
}
