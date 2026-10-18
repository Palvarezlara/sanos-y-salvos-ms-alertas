package com.sanosysalvos.ms_alertas.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.ContactoMensajeDto;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import software.amazon.awssdk.services.sqs.SqsClient;

@ExtendWith(MockitoExtension.class)
class ContactoMailListenerTest {

    private static final String REMITENTE = "notificaciones@sanosysalvos.cl";
    private static final String FRONTEND_URL = "http://localhost:5173";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SqsClient sqsClient;

    private ContactoMailListener listener;

    @BeforeEach
    void setUp() {
        // autoArranque=false: no levanta el hilo real de polling en el test
        listener = new ContactoMailListener(sqsClient, "http://dummy/cola-contacto", false,
                mailSender, REMITENTE, new ObjectMapper(), FRONTEND_URL);
    }

    private ContactoMensajeDto mensajeConToken() {
        return new ContactoMensajeDto(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "duena@example.com",
                "Luna",
                "María González",
                "maria@ejemplo.cl",
                "+56912345678",
                "Creo haber visto a tu mascota cerca de la plaza.",
                "token-abc");
    }

    @Test
    void enviarCorreo_crea_y_manda_un_mimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        listener.enviarCorreo(mensajeConToken());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void construirCuerpoHtml_incluye_el_boton_de_confirmacion_cuando_hay_token() {
        String html = listener.construirCuerpoHtml(mensajeConToken());

        assertThat(html)
                .contains("Luna", "Confirmar reunificación")
                .contains(FRONTEND_URL + "/confirmar-reunificacion/64f1a2b3c4d5e6f7a8b9c0d1?token=token-abc")
                .contains("<a href="); // es un botón real (link), no texto plano
    }

    @Test
    void construirCuerpoHtml_sin_token_no_incluye_boton() {
        ContactoMensajeDto mensaje = new ContactoMensajeDto(
                "64f1a2b3c4d5e6f7a8b9c0d1", "duena@example.com", "Luna", "María González",
                "maria@ejemplo.cl", "+56912345678", "Mensaje.", null);

        String html = listener.construirCuerpoHtml(mensaje);

        assertThat(html).doesNotContain("confirmar-reunificacion", "Confirmar reunificación");
    }

    @Test
    void construirCuerpoHtml_escapa_el_mensaje_del_contactante() {
        ContactoMensajeDto mensaje = new ContactoMensajeDto(
                "64f1a2b3c4d5e6f7a8b9c0d1", "duena@example.com", "Luna", "<script>alert(1)</script>",
                "maria@ejemplo.cl", null, "<b>hola</b>", null);

        String html = listener.construirCuerpoHtml(mensaje);

        assertThat(html).doesNotContain("<script>alert(1)</script>", "<b>hola</b>");
    }

    @Test
    void construirCuerpoTexto_mantiene_el_link_plano_para_clientes_sin_html() {
        String texto = listener.construirCuerpoTexto(mensajeConToken());

        assertThat(texto)
                .contains("Luna", mensajeConToken().getMensaje(), mensajeConToken().getNombreContacto())
                .contains(FRONTEND_URL + "/confirmar-reunificacion/64f1a2b3c4d5e6f7a8b9c0d1?token=token-abc");
    }
}
