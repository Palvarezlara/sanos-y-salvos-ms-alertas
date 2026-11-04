package com.sanosysalvos.ms_alertas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sanosysalvos.ms_alertas.exception.DemasiadasSolicitudesException;

class LimitadorContactosTest {

    /* reloj que avanza solo cuando el test lo pide */
    static class RelojManual extends Clock {
        Instant ahora = Instant.parse("2026-11-03T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private RelojManual reloj;
    private LimitadorContactos limitador;

    @BeforeEach
    void setUp() {
        reloj = new RelojManual();
        limitador = new LimitadorContactos(3, 2, Duration.ofHours(1), reloj);
    }

    @Test
    void limitaPorCorreoDeQuienContacta() {
        limitador.registrar("m-1", "a@x.cl");
        limitador.registrar("m-2", "a@x.cl");

        assertThatThrownBy(() -> limitador.registrar("m-3", "a@x.cl")).isInstanceOf(DemasiadasSolicitudesException.class);
        assertThatCode(() -> limitador.registrar("m-3", "otra@x.cl")).doesNotThrowAnyException();
    }

    @Test
    void elCorreoSeCompararSinMayusculasNiEspacios() {
        limitador.registrar("m-1", "A@x.cl");
        limitador.registrar("m-2", " a@X.cl ");

        assertThatThrownBy(() -> limitador.registrar("m-3", "a@x.cl")).isInstanceOf(DemasiadasSolicitudesException.class);
    }

    @Test
    void limitaPorReporteAunqueCambieElCorreo() {
        limitador.registrar("m-1", "a@x.cl");
        limitador.registrar("m-1", "b@x.cl");
        limitador.registrar("m-1", "c@x.cl");

        assertThatThrownBy(() -> limitador.registrar("m-1", "d@x.cl")).isInstanceOf(DemasiadasSolicitudesException.class);
    }

    @Test
    void despuesDeLaVentanaVuelveAPermitir() {
        limitador.registrar("m-1", "a@x.cl");
        limitador.registrar("m-2", "a@x.cl");
        assertThatThrownBy(() -> limitador.registrar("m-3", "a@x.cl")).isInstanceOf(DemasiadasSolicitudesException.class);

        reloj.ahora = reloj.ahora.plus(Duration.ofHours(1)).plusSeconds(5);

        assertThatCode(() -> limitador.registrar("m-3", "a@x.cl")).doesNotThrowAnyException();
    }

    @Test
    void elIntentoRechazadoNoCuentaYIndicaCuantoEsperar() {
        limitador.registrar("m-1", "a@x.cl");
        limitador.registrar("m-2", "a@x.cl");
        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(20));

        DemasiadasSolicitudesException ex = org.junit.jupiter.api.Assertions.assertThrows(
                DemasiadasSolicitudesException.class, () -> limitador.registrar("m-3", "a@x.cl"));

        /* faltan ~40 min para que venza el primer intento */
        assertThat(ex.getReintentarEnSegundos()).isBetween(2390L, 2410L);
    }

    @Test
    void sinCorreoSoloAplicaElLimiteDelReporte() {
        for (int i = 0; i < 3; i++) {
            limitador.registrar("m-1", null);
        }

        assertThatThrownBy(() -> limitador.registrar("m-1", null)).isInstanceOf(DemasiadasSolicitudesException.class);
    }
}
