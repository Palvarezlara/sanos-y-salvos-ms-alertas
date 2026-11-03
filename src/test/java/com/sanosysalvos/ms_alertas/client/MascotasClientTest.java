package com.sanosysalvos.ms_alertas.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sanosysalvos.ms_alertas.dto.MascotaContactoDto;
import com.sanosysalvos.ms_alertas.exception.ServicioNoDisponibleException;
import com.sun.net.httpserver.HttpServer;

/* Servidor HTTP real y local: no hace falta ms-mascotas para probar como se traducen sus respuestas */
class MascotasClientTest {

    private static final String TOKEN = "secreto-de-servicio-de-prueba-de-al-menos-32-caracteres";

    private HttpServer servidor;
    private String metodoRecibido;
    private String tokenRecibido;
    private int estado;
    private String cuerpo;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", exchange -> {
            metodoRecibido = exchange.getRequestMethod();
            tokenRecibido = exchange.getRequestHeaders().getFirst("X-Internal-Token");
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(estado, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private MascotasClient cliente() {
        return new MascotasClient("http://127.0.0.1:" + servidor.getAddress().getPort(), TOKEN);
    }

    @Test
    void devuelveElContactoCuandoMsMascotasResponde200() {
        estado = 200;
        cuerpo = "{\"mascotaId\":\"m1\",\"emailDestino\":\"dueno@example.com\",\"nombreMascota\":\"Luna\"}";

        MascotaContactoDto contacto = cliente().obtenerContacto("m1");

        assertThat(contacto.getEmailDestino()).isEqualTo("dueno@example.com");
    }

    @Test
    void unaMascotaInexistenteONoAprobadaDevuelveNullNoUnError() {
        estado = 404;
        cuerpo = "";

        assertThat(cliente().obtenerContacto("no-existe")).isNull();
    }

    @Test
    void unErrorDeMsMascotasSeTraduceAServicioNoDisponible() {
        estado = 500;
        cuerpo = "{}";

        assertThatThrownBy(() -> cliente().obtenerContacto("m1")).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void msMascotasCaidoSeTraduceAServicioNoDisponible() {
        MascotasClient sinServidor = new MascotasClient("http://127.0.0.1:1", TOKEN);

        assertThatThrownBy(() -> sinServidor.obtenerContacto("m1")).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void pideElContactoPorPostYConElSecretoDeServicio() {
        estado = 200;
        cuerpo = "{\"mascotaId\":\"m1\",\"emailDestino\":\"dueno@example.com\"}";

        cliente().obtenerContacto("m1");

        /* ms-mascotas ya no atiende GET en /internal/.../contacto y rechaza las llamadas sin el secreto */
        assertThat(metodoRecibido).isEqualTo("POST");
        assertThat(tokenRecibido).isEqualTo(TOKEN);
    }

    @Test
    void sinSecretoConfiguradoNoEnviaElHeader() {
        estado = 200;
        cuerpo = "{\"mascotaId\":\"m1\"}";

        new MascotasClient("http://127.0.0.1:" + servidor.getAddress().getPort(), "").obtenerContacto("m1");

        assertThat(tokenRecibido).isNull();
    }
}
