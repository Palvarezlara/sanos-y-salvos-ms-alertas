package com.sanosysalvos.ms_alertas.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que viaja por SNS/SQS entre ContactoServiceImpl (publica) y
 * ContactoMailListener (consume y recién ahí envía el correo). Ya trae el
 * destinatario resuelto, para que el listener no tenga que volver a llamar a
 * ms-mascotas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactoMensajeDto implements Serializable {
    private String mascotaId;
    private String emailDestino;
    private String nombreMascota;
    private String nombreContacto;
    private String emailContacto;
    private String telefonoContacto;
    private String mensaje;
    // Para armar, en el correo, el link de confirmación de reunificación
    // (ver MascotaService.confirmarReunificacion en ms-mascotas).
    private String tokenReunificacion;
}
