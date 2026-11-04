package com.sanosysalvos.ms_alertas.listener;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.sanosysalvos.ms_alertas.dto.MascotaReunificadaEvento;
import com.sanosysalvos.ms_alertas.service.AlertaService;

import software.amazon.awssdk.services.sqs.SqsClient;

@ExtendWith(MockitoExtension.class)
class MascotaReunificadaMailListenerTest {

    private static final String REMITENTE = "notificaciones@sanosysalvos.cl";

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private AlertaService alertaService;

    private MascotaReunificadaMailListener listener;

    @BeforeEach
    void setUp() {
        listener = new MascotaReunificadaMailListener(sqsClient, "http://dummy/cola-mascota-reunificada", false,
                mailSender, REMITENTE, new ObjectMapper(), alertaService);
    }

    @Test
    void enviarCorreo_notifica_a_quien_creo_el_reporte_que_se_cerro() {
        MascotaReunificadaEvento evento = new MascotaReunificadaEvento(
                "mascota-1", "Firulais", "duena@example.com");

        listener.enviarCorreo(evento);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage enviado = captor.getValue();
        assertThat(enviado.getFrom()).isEqualTo(REMITENTE);
        assertThat(enviado.getTo()).containsExactly("duena@example.com");
        assertThat(enviado.getSubject()).contains("Firulais");
        assertThat(enviado.getText()).contains("Firulais", "reunificado");
    }

    @Test
    void procesar_deserializa_el_json_de_sqs_y_envia_el_correo() throws Exception {
        String cuerpoMensaje = "{\"mascotaId\":\"mascota-2\",\"nombreMascota\":\"Copito\",\"emailContacto\":\"otra@example.com\"}";

        var procesarMetodo = MascotaReunificadaMailListener.class.getDeclaredMethod("procesar", String.class);
        procesarMetodo.setAccessible(true);
        procesarMetodo.invoke(listener, cuerpoMensaje);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("otra@example.com");
        verify(alertaService).desactivarPorMascota("mascota-2", "REUNIFICADA");
    }

    @Test
    void el_asunto_no_admite_saltos_de_linea_del_nombre_de_la_mascota() {
        // el nombre lo escribe el dueño: "Bcc:" en una segunda linea inyectaria un encabezado
        MascotaReunificadaEvento evento = new MascotaReunificadaEvento(
                "mascota-3", "Firu\r\nBcc: victima@example.com", "duena@example.com");

        listener.enviarCorreo(evento);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).doesNotContain("\r", "\n");
        assertThat(captor.getValue().getSubject()).contains("Firu");
    }

    @Test
    void sin_nombre_usa_un_texto_de_respaldo() {
        listener.enviarCorreo(new MascotaReunificadaEvento("mascota-4", "  ", "duena@example.com"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).contains("tu mascota");
    }
}
