package com.sanosysalvos.ms_alertas.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.ContactoMensajeDto;
import com.sanosysalvos.ms_alertas.messaging.SqsPollingConsumer;

import jakarta.mail.internet.MimeMessage;

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
    private final String frontendUrl;

    public ContactoMailListener(
            SqsClient sqsClient,
            @Value("${sqs.cola-contacto-url}") String queueUrl,
            @Value("${sqs.consumidor.auto-arranque:true}") boolean autoArranque,
            JavaMailSender mailSender,
            @Value("${contacto.remitente}") String remitente,
            ObjectMapper objectMapper,
            @Value("${frontend.url}") String frontendUrl) {
        super(sqsClient, queueUrl, autoArranque);
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.objectMapper = objectMapper;
        this.frontendUrl = frontendUrl;
    }

    @Override
    protected void procesar(String cuerpoMensaje) throws Exception {
        ContactoMensajeDto mensaje = objectMapper.readValue(cuerpoMensaje, ContactoMensajeDto.class);
        enviarCorreo(mensaje);
    }

    public void enviarCorreo(ContactoMensajeDto mensaje) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // multipart=true: permite adjuntar la version HTML y la de texto
            // plano juntas (alternative) — clientes que no rendericen HTML
            // igual pueden leer el mensaje y el link de texto plano.
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(mensaje.getEmailDestino());
            helper.setReplyTo(mensaje.getEmailContacto());
            helper.setSubject("Alguien tiene información sobre " + mensaje.getNombreMascota() + " — Sanos y Salvos");
            helper.setText(construirCuerpoTexto(mensaje), construirCuerpoHtml(mensaje));

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            // mailSender.send(MimeMessage) no declara checked exceptions en la
            // firma de JavaMailSender, pero MimeMessageHelper sí las lanza al
            // armar el mensaje (MessagingException) — se envuelve para que
            // SqsPollingConsumer la trate igual que cualquier otro fallo de
            // procesamiento (no borra el mensaje, reintenta).
            throw new IllegalStateException("No se pudo armar o enviar el correo de contacto", e);
        }

        // No se registra el correo ni el contenido del mensaje en el log.
        log.info("Correo de contacto enviado para mascotaId={}", mensaje.getMascotaId());
    }

    String construirCuerpoTexto(ContactoMensajeDto mensaje) {
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
        if (tieneTokenReunificacion(mensaje)) {
            cuerpo.append("¿Esta persona te ayudó a recuperar a ").append(mensaje.getNombreMascota()).append("?\n");
            cuerpo.append("Confirma la reunificación acá: ").append(linkConfirmacion(mensaje)).append("\n\n");
        }
        cuerpo.append("— Sanos y Salvos");
        return cuerpo.toString();
    }

    String construirCuerpoHtml(ContactoMensajeDto mensaje) {
        String nombreMascota = HtmlUtils.htmlEscape(mensaje.getNombreMascota());
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:15px;color:#2B2B2B;line-height:1.5;\">");
        html.append("<p>Hola,</p>");
        html.append("<p><strong>").append(HtmlUtils.htmlEscape(mensaje.getNombreContacto()))
                .append("</strong> dejó este mensaje sobre <strong>").append(nombreMascota)
                .append("</strong> en Sanos y Salvos:</p>");
        html.append("<blockquote style=\"margin:0 0 16px;padding:12px 16px;background:#FFECF2;border-left:4px solid #C46081;border-radius:8px;\">")
                .append(HtmlUtils.htmlEscape(mensaje.getMensaje()))
                .append("</blockquote>");
        html.append("<p>Correo de contacto: ").append(HtmlUtils.htmlEscape(mensaje.getEmailContacto())).append("<br/>");
        if (mensaje.getTelefonoContacto() != null && !mensaje.getTelefonoContacto().isBlank()) {
            html.append("Teléfono: ").append(HtmlUtils.htmlEscape(mensaje.getTelefonoContacto())).append("<br/>");
        }
        html.append("</p>");
        html.append("<p>Puedes responder directamente a este correo para contactar a esta persona.</p>");
        if (tieneTokenReunificacion(mensaje)) {
            html.append("<p>¿Esta persona te ayudó a recuperar a ").append(nombreMascota).append("?</p>");
            html.append("<p style=\"text-align:center;margin:24px 0;\">")
                    .append("<a href=\"").append(linkConfirmacion(mensaje)).append("\" ")
                    .append("style=\"display:inline-block;padding:14px 28px;background:#99A966;color:#ffffff;")
                    .append("font-weight:bold;text-decoration:none;border-radius:16px;font-size:15px;\">")
                    .append("🎉 Confirmar reunificación</a></p>");
        }
        html.append("<p>— Sanos y Salvos</p>");
        html.append("</div>");
        return html.toString();
    }

    private boolean tieneTokenReunificacion(ContactoMensajeDto mensaje) {
        return mensaje.getTokenReunificacion() != null && !mensaje.getTokenReunificacion().isBlank();
    }

    private String linkConfirmacion(ContactoMensajeDto mensaje) {
        return frontendUrl + "/confirmar-reunificacion/" + mensaje.getMascotaId()
                + "?token=" + mensaje.getTokenReunificacion();
    }
}
