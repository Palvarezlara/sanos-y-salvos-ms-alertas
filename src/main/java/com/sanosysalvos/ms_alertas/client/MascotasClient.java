package com.sanosysalvos.ms_alertas.client;

import java.time.Duration;

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
 */
@Component
public class MascotasClient {

    private final RestClient restClient;

    public MascotasClient(@Value("${ms-mascotas.base-url}") String baseUrl) {
        // Sin timeouts, un ms-mascotas colgado dejaria bloqueados los hilos de /contactos
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica).build();
    }

    /** null si la mascota no existe o su reporte no esta aprobado (404 de ms-mascotas). */
    public MascotaContactoDto obtenerContacto(String mascotaId) {
        try {
            /* ms-mascotas sirve todo bajo /api/v1 (SPRING_MVC_SERVLET_PATH); sin el
             * prefijo la peticion no matcheaba el permitAll de /internal/** y caia en 401 */
            return restClient.get()
                    .uri("/api/v1/internal/mascotas/{id}/contacto", mascotaId)
                    .retrieve()
                    .body(MascotaContactoDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new ServicioNoDisponibleException("No se pudo consultar ms-mascotas", e);
        }
    }
}
