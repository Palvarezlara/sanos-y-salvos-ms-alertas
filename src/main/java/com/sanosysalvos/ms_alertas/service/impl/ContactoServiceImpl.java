package com.sanosysalvos.ms_alertas.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.sanosysalvos.ms_alertas.client.MascotasClient;
import com.sanosysalvos.ms_alertas.dto.ContactoRequestDto;
import com.sanosysalvos.ms_alertas.dto.MascotaContactoDto;
import com.sanosysalvos.ms_alertas.exception.ResourceNotFoundException;
import com.sanosysalvos.ms_alertas.service.ContactoService;

@Service
public class ContactoServiceImpl implements ContactoService {

    private static final Logger log = LoggerFactory.getLogger(ContactoServiceImpl.class);

    private final MascotasClient mascotasClient;
    private final JavaMailSender mailSender;
    private final String remitente;

    public ContactoServiceImpl(
            MascotasClient mascotasClient,
            JavaMailSender mailSender,
            @Value("${contacto.remitente}") String remitente) {
        this.mascotasClient = mascotasClient;
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public void contactar(ContactoRequestDto dto) {
        MascotaContactoDto destino = mascotasClient.obtenerContacto(dto.getMascotaId());
        if (destino == null || destino.getEmailDestino() == null || destino.getEmailDestino().isBlank()) {
            throw new ResourceNotFoundException("No se encontró un contacto para la mascota " + dto.getMascotaId());
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destino.getEmailDestino());
        mensaje.setReplyTo(dto.getEmailContacto());
        mensaje.setSubject("Alguien tiene información sobre " + destino.getNombreMascota() + " — Sanos y Salvos");
        mensaje.setText(construirCuerpo(dto, destino));

        mailSender.send(mensaje);

        // No se registra el correo ni el contenido del mensaje en el log.
        log.info("Correo de contacto enviado para mascotaId={}", dto.getMascotaId());
    }

    private String construirCuerpo(ContactoRequestDto dto, MascotaContactoDto destino) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("Hola,\n\n");
        cuerpo.append(dto.getNombreContacto())
                .append(" dejó este mensaje sobre ")
                .append(destino.getNombreMascota())
                .append(" en Sanos y Salvos:\n\n");
        cuerpo.append('"').append(dto.getMensaje()).append('"').append("\n\n");
        cuerpo.append("Correo de contacto: ").append(dto.getEmailContacto()).append('\n');
        if (dto.getTelefonoContacto() != null && !dto.getTelefonoContacto().isBlank()) {
            cuerpo.append("Teléfono: ").append(dto.getTelefonoContacto()).append('\n');
        }
        cuerpo.append("\nPuedes responder directamente a este correo para contactar a esta persona.\n\n");
        cuerpo.append("— Sanos y Salvos");
        return cuerpo.toString();
    }
}
