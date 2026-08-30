package com.sanosysalvos.ms_alertas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.sanosysalvos.ms_alertas.client.MascotasClient;
import com.sanosysalvos.ms_alertas.dto.ContactoRequestDto;
import com.sanosysalvos.ms_alertas.dto.MascotaContactoDto;
import com.sanosysalvos.ms_alertas.exception.ResourceNotFoundException;
import com.sanosysalvos.ms_alertas.service.impl.ContactoServiceImpl;

@ExtendWith(MockitoExtension.class)
class ContactoServiceImplTest {

    private static final String REMITENTE = "notificaciones@sanosysalvos.cl";

    @Mock
    private MascotasClient mascotasClient;

    @Mock
    private JavaMailSender mailSender;

    private ContactoService contactoService;

    @BeforeEach
    void setUp() {
        contactoService = new ContactoServiceImpl(mascotasClient, mailSender, REMITENTE);
    }

    private ContactoRequestDto crearRequest() {
        ContactoRequestDto dto = new ContactoRequestDto();
        dto.setMascotaId("64f1a2b3c4d5e6f7a8b9c0d1");
        dto.setNombreContacto("María González");
        dto.setEmailContacto("maria@ejemplo.cl");
        dto.setTelefonoContacto("+56912345678");
        dto.setMensaje("Creo haber visto a tu mascota cerca de la plaza.");
        return dto;
    }

    @Test
    void contactar_envia_correo_al_dueno_con_replyTo_del_contactante() {
        ContactoRequestDto request = crearRequest();
        when(mascotasClient.obtenerContacto(request.getMascotaId()))
                .thenReturn(new MascotaContactoDto(request.getMascotaId(), "duena@example.com", "Luna"));

        contactoService.contactar(request);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage enviado = captor.getValue();
        assertThat(enviado.getFrom()).isEqualTo(REMITENTE);
        assertThat(enviado.getTo()).containsExactly("duena@example.com");
        assertThat(enviado.getReplyTo()).isEqualTo(request.getEmailContacto());
        assertThat(enviado.getText()).contains("Luna", request.getMensaje(), request.getNombreContacto());
    }

    @Test
    void contactar_lanza_notFound_si_ms_mascotas_no_tiene_destinatario() {
        ContactoRequestDto request = crearRequest();
        when(mascotasClient.obtenerContacto(request.getMascotaId())).thenReturn(null);

        assertThatThrownBy(() -> contactoService.contactar(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
    }
}
