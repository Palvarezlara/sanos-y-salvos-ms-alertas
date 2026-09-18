package com.sanosysalvos.ms_alertas.client;

import com.sanosysalvos.ms_alertas.dto.MascotaContactoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente hacia el endpoint interno de ms-mascotas que resuelve el destinatario
 * de un correo de contacto, sin que ms-alertas acceda directo a su base de datos.
 */
@Component
public class MascotasClient {

    private final RestClient restClient;

    public MascotasClient(@Value("${ms-mascotas.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public MascotaContactoDto obtenerContacto(String mascotaId) {
        /* ms-mascotas sirve todo bajo /api/v1 (SPRING_MVC_SERVLET_PATH); sin el
         * prefijo la petición no matcheaba el permitAll de /internal/** y caía en 401 */
        return restClient.get()
                .uri("/api/v1/internal/mascotas/{id}/contacto", mascotaId)
                .retrieve()
                .body(MascotaContactoDto.class);
    }
}
