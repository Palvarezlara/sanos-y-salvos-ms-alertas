package com.sanosysalvos.ms_alertas.client;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sanosysalvos.ms_alertas.dto.MascotaContactoDto;
import com.sanosysalvos.ms_alertas.exception.ServicioNoDisponibleException;

/**
 * Cliente hacia el endpoint interno de ms-mascotas que resuelve el destinatario
 * de un correo de contacto, sin que ms-alertas acceda directo a su base de datos.
 *
 * ms-mascotas exige el secreto de servicio en X-Internal-Token (INTERNAL_API_TOKEN) y ahora atiende el
 * contacto por POST: puede emitir un token de reunificación, así que ya no es una simple lectura.
 */
@Component
public class MascotasClient {

    private static final Logger log = LoggerFactory.getLogger(MascotasClient.class);

    private final RestClient restClient;

    public MascotasClient(@Value("${ms-mascotas.base-url}") String baseUrl,
            @Value("${internal.api-token:}") String tokenInterno) {
        // Sin el secreto, ms-mascotas rechaza todo con 401 y parecia una caida del servicio: se falla al arrancar
        if (tokenInterno == null || tokenInterno.strip().length() < 32) {
            throw new IllegalStateException(
                    "INTERNAL_API_TOKEN no esta definido (minimo 32 caracteres): ms-mascotas rechazaria las consultas internas");
        }
        // Sin timeouts, un ms-mascotas colgado dejaria bloqueados los hilos de /contactos
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica)
                .defaultHeader("X-Internal-Token", tokenInterno.strip()).build();
    }

    /** null si la mascota no existe o su reporte no esta aprobado (404 de ms-mascotas). */
    public MascotaContactoDto obtenerContacto(String mascotaId) {
        try {
            /* ms-mascotas sirve todo bajo /api/v1 (SPRING_MVC_SERVLET_PATH); sin el
             * prefijo la peticion no matcheaba el permitAll de /internal/** y caia en 401 */
            return restClient.post()
                    .uri("/api/v1/internal/mascotas/{id}/contacto", mascotaId)
                    .retrieve()
                    .body(MascotaContactoDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            // es configuracion (secreto distinto en ms-mascotas), no una caida: se deja claro en el log
            log.error("ms-mascotas rechazo la credencial de servicio (HTTP {}): revisar INTERNAL_API_TOKEN en ambos servicios",
                    e.getStatusCode().value());
            throw new ServicioNoDisponibleException("ms-mascotas rechazo la credencial de servicio", e);
        } catch (RestClientException e) {
            throw new ServicioNoDisponibleException("No se pudo consultar ms-mascotas", e);
        }
    }
}
