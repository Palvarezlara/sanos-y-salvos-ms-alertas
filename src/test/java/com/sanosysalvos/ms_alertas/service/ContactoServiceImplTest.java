package com.sanosysalvos.ms_alertas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanosysalvos.ms_alertas.client.MascotasClient;
import com.sanosysalvos.ms_alertas.dto.ContactoMensajeDto;
import com.sanosysalvos.ms_alertas.dto.ContactoRequestDto;
import com.sanosysalvos.ms_alertas.dto.MascotaContactoDto;
import com.sanosysalvos.ms_alertas.exception.ResourceNotFoundException;
import com.sanosysalvos.ms_alertas.messaging.SnsEventPublisher;
import com.sanosysalvos.ms_alertas.service.impl.ContactoServiceImpl;

@ExtendWith(MockitoExtension.class)
class ContactoServiceImplTest {

    @Mock
    private MascotasClient mascotasClient;

    @Mock
    private SnsEventPublisher snsEventPublisher;

    private ContactoService contactoService;

    @BeforeEach
    void setUp() {
        contactoService = new ContactoServiceImpl(mascotasClient, snsEventPublisher);
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
    void contactar_publica_el_mensaje_con_el_destinatario_resuelto() {
        ContactoRequestDto request = crearRequest();
        when(mascotasClient.obtenerContacto(request.getMascotaId()))
                .thenReturn(new MascotaContactoDto(request.getMascotaId(), "duena@example.com", "Luna", "token-abc"));

        contactoService.contactar(request);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(snsEventPublisher).publicar(eq(ContactoServiceImpl.TIPO_EVENTO_CONTACTO), captor.capture());

        ContactoMensajeDto publicado = (ContactoMensajeDto) captor.getValue();
        assertThat(publicado.getEmailDestino()).isEqualTo("duena@example.com");
        assertThat(publicado.getNombreMascota()).isEqualTo("Luna");
        assertThat(publicado.getEmailContacto()).isEqualTo(request.getEmailContacto());
        assertThat(publicado.getNombreContacto()).isEqualTo(request.getNombreContacto());
        assertThat(publicado.getMensaje()).isEqualTo(request.getMensaje());
        assertThat(publicado.getTokenReunificacion()).isEqualTo("token-abc");
    }

    @Test
    void contactar_lanza_notFound_si_ms_mascotas_no_tiene_destinatario() {
        ContactoRequestDto request = crearRequest();
        when(mascotasClient.obtenerContacto(request.getMascotaId())).thenReturn(null);

        assertThatThrownBy(() -> contactoService.contactar(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(snsEventPublisher, never()).publicar(any(String.class), any(Object.class));
    }
}
