package com.sanosysalvos.ms_alertas.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sanosysalvos.ms_alertas.client.MascotasClient;
import com.sanosysalvos.ms_alertas.dto.ContactoMensajeDto;
import com.sanosysalvos.ms_alertas.dto.ContactoRequestDto;
import com.sanosysalvos.ms_alertas.dto.MascotaContactoDto;
import com.sanosysalvos.ms_alertas.exception.ResourceNotFoundException;
import com.sanosysalvos.ms_alertas.messaging.SnsEventPublisher;
import com.sanosysalvos.ms_alertas.service.ContactoService;

@Service
public class ContactoServiceImpl implements ContactoService {

    private static final Logger log = LoggerFactory.getLogger(ContactoServiceImpl.class);

    public static final String TIPO_EVENTO_CONTACTO = "contacto.solicitado";

    private final MascotasClient mascotasClient;
    private final SnsEventPublisher snsEventPublisher;

    public ContactoServiceImpl(MascotasClient mascotasClient, SnsEventPublisher snsEventPublisher) {
        this.mascotasClient = mascotasClient;
        this.snsEventPublisher = snsEventPublisher;
    }

    @Override
    public void contactar(ContactoRequestDto dto) {
        MascotaContactoDto destino = mascotasClient.obtenerContacto(dto.getMascotaId());
        if (destino == null || destino.getEmailDestino() == null || destino.getEmailDestino().isBlank()) {
            throw new ResourceNotFoundException("No se encontró un contacto para la mascota " + dto.getMascotaId());
        }

        ContactoMensajeDto mensaje = new ContactoMensajeDto(
                dto.getMascotaId(),
                destino.getEmailDestino(),
                destino.getNombreMascota(),
                dto.getNombreContacto(),
                dto.getEmailContacto(),
                dto.getTelefonoContacto(),
                dto.getMensaje());

        snsEventPublisher.publicar(TIPO_EVENTO_CONTACTO, mensaje);

        // No se registra el correo ni el contenido del mensaje en el log.
        log.info("Mensaje de contacto encolado para mascotaId={}", dto.getMascotaId());
    }
}
