package com.sanosysalvos.ms_alertas.listener;

/**
 * Limpieza de texto escrito por usuarios antes de ponerlo en el asunto de un correo. Un salto de línea con
 * "Bcc:" permitiría inyectar encabezados y usar la cuenta oficial para escribirle a destinatarios arbitrarios.
 */
final class TextoSeguro {

    static final int LARGO_MAXIMO_NOMBRE = 60;

    private TextoSeguro() {
    }

    /** Sin caracteres de control (incluye CR y LF), recortado a 60 caracteres; vacío → el respaldo. */
    static String nombre(String nombre, String respaldo) {
        String limpio = nombre == null ? "" : nombre.replaceAll("[\\p{Cntrl}]", " ").strip();
        if (limpio.isEmpty()) {
            return respaldo;
        }
        return limpio.length() > LARGO_MAXIMO_NOMBRE ? limpio.substring(0, LARGO_MAXIMO_NOMBRE) + "…" : limpio;
    }
}
