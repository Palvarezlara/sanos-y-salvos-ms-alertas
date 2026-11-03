package com.sanosysalvos.ms_alertas.exception;

/** Un servicio del que depende esta operacion (ej. ms-mascotas) no respondio a tiempo o fallo. */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
