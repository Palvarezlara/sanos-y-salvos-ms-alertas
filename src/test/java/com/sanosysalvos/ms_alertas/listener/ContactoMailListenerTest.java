package com.sanosysalvos.ms_alertas.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.ContactoMensajeDto;

import software.amazon.awssdk.services.sqs.SqsClient;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContactoMailListenerTest {

    private static final String REMITENTE = "notificaciones@sanosysalvos.cl";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SqsClient sqsClient;

    private ContactoMailListener listener;

    private static final String FRONTEND_URL = "http://localhost:5173";

    @BeforeEach
    void setUp() {
        // autoArranque=false: no levanta el hilo real de polling en el test
        listener = new ContactoMailListener(sqsClient, "http://dummy/cola-contacto", false,
                mailSender, REMITENTE, new ObjectMapper(), FRONTEND_URL);
    }

    @Test
    void enviarCorreo_arma_y_envia_el_correo_con_replyTo_del_contactante() {
        ContactoMensajeDto mensaje = new ContactoMensajeDto(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "duena@example.com",
                "Luna",
                "María González",
                "maria@ejemplo.cl",
                "+56912345678",
                "Creo haber visto a tu mascota cerca de la plaza.",
                "token-abc");

        listener.enviarCorreo(mensaje);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage enviado = captor.getValue();
        assertThat(enviado.getFrom()).isEqualTo(REMITENTE);
        assertThat(enviado.getTo()).containsExactly("duena@example.com");
        assertThat(enviado.getReplyTo()).isEqualTo(mensaje.getEmailContacto());
        assertThat(enviado.getText()).contains("Luna", mensaje.getMensaje(), mensaje.getNombreContacto());
        assertThat(enviado.getText())
                .contains(FRONTEND_URL + "/confirmar-reunificacion/64f1a2b3c4d5e6f7a8b9c0d1?token=token-abc");
    }

    @Test
    void enviarCorreo_sin_token_no_incluye_link_de_confirmacion() {
        ContactoMensajeDto mensaje = new ContactoMensajeDto(
                "64f1a2b3c4d5e6f7a8b9c0d1",
                "duena@example.com",
                "Luna",
                "María González",
                "maria@ejemplo.cl",
                "+56912345678",
                "Creo haber visto a tu mascota cerca de la plaza.",
                null);

        listener.enviarCorreo(mensaje);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).doesNotContain("confirmar-reunificacion");
    }
}
