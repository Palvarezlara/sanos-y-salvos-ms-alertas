package com.sanosysalvos.ms_alertas.listener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.ChatAvisoEvento;
import com.sanosysalvos.ms_alertas.messaging.SqsPollingConsumer;

import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Consume cola-chat-aviso: ms-chat publica "chat.mensaje.nuevo" cuando alguien recibe un mensaje
 * y no está conectado, y acá se le avisa por correo. El correo NO incluye el texto del mensaje,
 * solo el nombre de la mascota y el enlace al chat (donde hay que iniciar sesión para leerlo).
 */
@Component
public class ChatAvisoMailListener extends SqsPollingConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatAvisoMailListener.class);
    /* sin espacios ni saltos de línea: evita inyectar encabezados de correo (Bcc:, etc.) */
    private static final String PATRON_CORREO = "[^\\s@\\r\\n]+@[^\\s@\\r\\n]+\\.[^\\s@\\r\\n]+";

    private final JavaMailSender mailSender;
    private final String remitente;
    private final ObjectMapper objectMapper;
    private final String frontendUrl;

    public ChatAvisoMailListener(
            SqsClient sqsClient,
            @Value("${sqs.cola-chat-aviso-url:}") String queueUrl,
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
        enviarCorreo(objectMapper.readValue(cuerpoMensaje, ChatAvisoEvento.class));
    }

    public void enviarCorreo(ChatAvisoEvento evento) {
        String destino = evento.getEmailDestino() == null ? "" : evento.getEmailDestino().strip();
        if (destino.length() > 150 || !destino.matches(PATRON_CORREO)) {
            /* evento mal formado: reintentarlo no lo arregla; se descarta (retornar lo borra de la cola) */
            log.warn("Aviso de chat descartado: destinatario inválido, conversacionId={}", evento.getConversacionId());
            return;
        }
        String mascota = nombreSeguro(evento.getMascotaNombre());

        SimpleMailMessage correo = new SimpleMailMessage();
        correo.setFrom(remitente);
        correo.setTo(destino);
        correo.setSubject("Tienes un mensaje nuevo sobre " + mascota + " — Sanos y Salvos");
        correo.setText(construirCuerpo(mascota, evento.getConversacionId()));
        mailSender.send(correo);

        /* no se registra el correo del destinatario */
        log.info("Aviso de mensaje nuevo enviado, conversacionId={}", evento.getConversacionId());
    }

    /* el nombre lo escribe un usuario y va en el asunto: se quitan saltos de línea y se acorta */
    static String nombreSeguro(String nombre) {
        return TextoSeguro.nombre(nombre, "tu reporte");
    }

    String construirCuerpo(String mascota, String conversacionId) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        String enlace = base + "/chat";
        if (conversacionId != null && !conversacionId.isBlank()) {
            enlace += "?c=" + URLEncoder.encode(conversacionId, StandardCharsets.UTF_8);
        }
        return "Hola,\n\n"
                + "Tienes un mensaje nuevo sobre " + mascota + " en Sanos y Salvos.\n\n"
                + "Entra para leerlo y responder (necesitas iniciar sesión):\n"
                + enlace + "\n\n"
                + "Por tu privacidad, no incluimos el contenido del mensaje en este correo. "
                + "Si no esperabas este aviso, puedes ignorarlo.\n\n"
                + "— Sanos y Salvos";
    }
}
