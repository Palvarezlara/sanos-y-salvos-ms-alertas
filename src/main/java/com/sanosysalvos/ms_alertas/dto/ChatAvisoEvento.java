package com.sanosysalvos.ms_alertas.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espejo de AvisoMensajeEvento de ms-chat ("chat.mensaje.nuevo"): alguien recibió un mensaje y no
 * tiene el chat abierto. Nunca trae el texto del mensaje, solo lo necesario para avisar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAvisoEvento implements Serializable {
    private String conversacionId;
    private String mascotaNombre;
    private String emailDestino;
}
