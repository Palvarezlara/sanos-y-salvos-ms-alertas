package com.sanosysalvos.ms_alertas.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextoSeguroTest {

    @Test
    void quita_saltos_de_linea_y_caracteres_de_control() {
        String limpio = TextoSeguro.nombre("Luna\r\nBcc: x@y.cl\tcon tab\u0000", "respaldo");

        assertThat(limpio).doesNotContain("\r", "\n", "\t", "\u0000");
        assertThat(limpio).startsWith("Luna");
    }

    @Test
    void acorta_los_nombres_largos() {
        String limpio = TextoSeguro.nombre("a".repeat(500), "respaldo");

        assertThat(limpio).hasSize(TextoSeguro.LARGO_MAXIMO_NOMBRE + 1).endsWith("…");
    }

    @Test
    void usa_el_respaldo_si_no_hay_nombre() {
        assertThat(TextoSeguro.nombre(null, "tu reporte")).isEqualTo("tu reporte");
        assertThat(TextoSeguro.nombre("  \r\n ", "tu reporte")).isEqualTo("tu reporte");
    }

    @Test
    void conserva_un_nombre_normal() {
        assertThat(TextoSeguro.nombre("Firulais", "x")).isEqualTo("Firulais");
    }
}
