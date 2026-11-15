package com.sanosysalvos.ms_alertas.exception;

/** Se superó el límite de uso de un endpoint público (ej. POST /contactos). */
public class DemasiadasSolicitudesException extends RuntimeException {

    private final long reintentarEnSegundos;

    public DemasiadasSolicitudesException(long reintentarEnSegundos) {
        super("Demasiadas solicitudes");
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
