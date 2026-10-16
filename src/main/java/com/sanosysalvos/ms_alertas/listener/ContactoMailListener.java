package com.sanosysalvos.ms_alertas.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.sanosysalvos.ms_alertas.config.RabbitMQConfig;
import com.sanosysalvos.ms_alertas.dto.ContactoMensajeDto;

/**
 * Consume cola.contacto y recién acá llama a JavaMailSender — antes esto
 * corría en el mismo hilo que la petición del usuario (ContactoServiceImpl),
 * bloqueándola mientras SMTP respondía.
 *
 * Si send() lanza una excepción, Spring reintenta según
 * spring.rabbitmq.listener.simple.retry; agotados los intentos, el mensaje
 * cae en cola.contacto.fallidos en vez de perderse (ver RabbitMQConfig).
 */
@Component
public class ContactoMailListener {

    private static final Logger log = LoggerFactory.getLogger(ContactoMailListener.class);

    private final JavaMailSender mailSender;
    private final String remitente;

    public ContactoMailListener(JavaMailSender mailSender, @Value("${contacto.remitente}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @RabbitListener(queues = RabbitMQConfig.COLA_CONTACTO)
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
