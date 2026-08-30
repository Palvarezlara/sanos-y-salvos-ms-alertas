package com.sanosysalvos.ms_alertas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.sanosysalvos.ms_alertas.model.Alerta;
import com.sanosysalvos.ms_alertas.model.EstadoAlerta;

// @DataJpaTest no está disponible con los starters de test divididos de Spring
// Boot 4 en este proyecto (no hay spring-boot-test-autoconfigure en el
// classpath de test) — se usa @SpringBootTest + @Transactional para levantar
// el contexto completo contra H2 y revertir los datos de cada test.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AlertaRepositoryTest {

    @Autowired
    private AlertaRepository alertaRepository;

    // Plaza de Armas, Santiago — punto de búsqueda usado en todos los casos.
    private static final double LAT_BUSQUEDA = -33.4372;
    private static final double LNG_BUSQUEDA = -70.6506;

    @Test
    void buscarPorZona_incluye_alertas_activas_dentro_del_radio_y_excluye_el_resto() {
        Alerta cercanaActiva = alertaRepository.save(Alerta.builder()
                .mascotaId("mascota-cercana")
                .latitudCentro(-33.44) // a pocos cientos de metros
                .longitudCentro(-70.65)
                .radioKm(3.0)
                .estado(EstadoAlerta.ACTIVA)
                .creadaEn(java.time.LocalDateTime.now())
                .build());

        alertaRepository.save(Alerta.builder()
                .mascotaId("mascota-lejana")
                .latitudCentro(-33.60) // Puente Alto, ~18km de distancia
                .longitudCentro(-70.58)
                .radioKm(3.0)
                .estado(EstadoAlerta.ACTIVA)
                .creadaEn(java.time.LocalDateTime.now())
                .build());

        alertaRepository.save(Alerta.builder()
                .mascotaId("mascota-cercana-pero-inactiva")
                .latitudCentro(-33.44)
                .longitudCentro(-70.65)
                .radioKm(3.0)
                .estado(EstadoAlerta.INACTIVA)
                .creadaEn(java.time.LocalDateTime.now())
                .build());

        List<Alerta> resultado = alertaRepository.buscarPorZona(LAT_BUSQUEDA, LNG_BUSQUEDA, 5.0);

        assertThat(resultado).extracting(Alerta::getMascotaId).containsExactly(cercanaActiva.getMascotaId());
    }
}
