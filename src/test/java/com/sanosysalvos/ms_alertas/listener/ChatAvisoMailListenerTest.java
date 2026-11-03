package com.sanosysalvos.ms_alertas.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanosysalvos.ms_alertas.dto.ChatAvisoEvento;

import software.amazon.awssdk.services.sqs.SqsClient;

@ExtendWith(MockitoExtension.class)
class ChatAvisoMailListenerTest {

    private static final String REMITENTE = "notificaciones@sanosysalvos.cl";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SqsClient sqsClient;

    private ChatAvisoMailListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatAvisoMailListener(sqsClient, "http://dummy/cola-chat-aviso", false,
                mailSender, REMITENTE, new ObjectMapper(), "https://sanosysalvos.example/");
    }

    private SimpleMailMessage enviado() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void avisaAlDestinatarioConElNombreDeLaMascotaYElEnlaceAlChat() {
        listener.enviarCorreo(new ChatAvisoEvento("c1", "Firulais", "persona@example.com"));

        SimpleMailMessage correo = enviado();
        assertThat(correo.getFrom()).isEqualTo(REMITENTE);
        assertThat(correo.getTo()).containsExactly("persona@example.com");
        assertThat(correo.getSubject()).contains("Firulais");
        assertThat(correo.getText()).contains("Firulais", "https://sanosysalvos.example/chat?c=c1");
    }

    @Test
    void elCorreoNoIncluyeContenidoDeMensajesPorqueElEventoNoLoTrae() {
        listener.enviarCorreo(new ChatAvisoEvento("c1", "Firulais", "persona@example.com"));

        assertThat(enviado().getText()).contains("no incluimos el contenido del mensaje");
    }

    @Test
    void elIdDeLaConversacionSeCodificaEnElEnlace() {
        listener.enviarCorreo(new ChatAvisoEvento("c 1&x=y", "Firu", "persona@example.com"));

        assertThat(enviado().getText()).contains("/chat?c=c+1%26x%3Dy").doesNotContain("&x=y");
    }

    @Test
    void unNombreConSaltosDeLineaNoPuedeInyectarEncabezadosEnElAsunto() {
        listener.enviarCorreo(new ChatAvisoEvento("c1", "Firu\r\nBcc: victima@example.com", "persona@example.com"));

        assertThat(enviado().getSubject()).doesNotContain("\r").doesNotContain("\n");
    }

    @Test
    void unNombreVacioOMuyLargoSeAcorta() {
        assertThat(ChatAvisoMailListener.nombreSeguro(null)).isEqualTo("tu reporte");
        assertThat(ChatAvisoMailListener.nombreSeguro("   ")).isEqualTo("tu reporte");
        assertThat(ChatAvisoMailListener.nombreSeguro("x".repeat(200))).hasSize(61);
    }

    @Test
    void conUnDestinatarioInvalidoNoEnviaNadaNiLanzaError() {
        listener.enviarCorreo(new ChatAvisoEvento("c1", "Firu", "no-es-correo"));
        listener.enviarCorreo(new ChatAvisoEvento("c1", "Firu", "a@example.com\r\nBcc: b@example.com"));
        listener.enviarCorreo(new ChatAvisoEvento("c1", "Firu", null));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void procesarDeserializaElJsonDeSqsYEnviaElCorreo() throws Exception {
        String cuerpo = "{\"conversacionId\":\"c9\",\"mascotaNombre\":\"Luna\",\"emailDestino\":\"duena@example.com\"}";

        var procesar = ChatAvisoMailListener.class.getDeclaredMethod("procesar", String.class);
        procesar.setAccessible(true);
        procesar.invoke(listener, cuerpo);

        assertThat(enviado().getTo()).containsExactly("duena@example.com");
    }
}
