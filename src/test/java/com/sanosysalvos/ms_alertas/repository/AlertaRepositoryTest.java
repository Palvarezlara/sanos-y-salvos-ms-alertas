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

    @Test
    void buscarPorZona_encuentra_la_alerta_ubicada_exactamente_en_el_punto_buscado() {
        // Por redondeo, cos/sin pueden sumar 1.0000000000000002 y acos() devolvia NULL (MySQL)
        // o error (H2): la alerta puesta justo en el punto desaparecia del resultado.
        double[][] puntos = { { -46.158, -75.9152 }, { -28.5431, -70.9113 }, { -19.9224, -71.4223 }, { -51.8318, -70.8564 } }; // cos/sin suman 1.0000000000000002
        for (double[] punto : puntos) {
            alertaRepository.save(Alerta.builder()
                    .mascotaId("exacta-" + punto[0])
                    .latitudCentro(punto[0])
                    .longitudCentro(punto[1])
                    .radioKm(1.5)
                    .estado(EstadoAlerta.ACTIVA)
                    .creadaEn(java.time.LocalDateTime.now())
                    .build());
        }

        for (double[] punto : puntos) {
            assertThat(alertaRepository.buscarPorZona(punto[0], punto[1], 0.1))
                    .extracting(Alerta::getMascotaId)
                    .contains("exacta-" + punto[0]);
        }
    }

    private Alerta alerta(String mascotaId, double lat, double lng, EstadoAlerta estado) {
        return Alerta.builder().mascotaId(mascotaId).latitudCentro(lat).longitudCentro(lng).radioKm(1.5)
                .estado(estado).creadaEn(java.time.LocalDateTime.now()).build();
    }

    @Test
    void la_caja_de_busqueda_no_deja_fuera_puntos_dentro_del_radio_hacia_el_este_ni_hacia_el_oeste() {
        // a latitud -33.44 un grado de longitud mide ~92,9 km: 0,0205 grados son ~1,9 km
        double lat = -33.44;
        alertaRepository.save(alerta("este-dentro", lat, -70.60 + 0.0205, EstadoAlerta.ACTIVA));
        alertaRepository.save(alerta("oeste-dentro", lat, -70.60 - 0.0205, EstadoAlerta.ACTIVA));
        alertaRepository.save(alerta("este-fuera", lat, -70.60 + 0.0235, EstadoAlerta.ACTIVA));   // ~2,2 km
        alertaRepository.save(alerta("norte-dentro", lat + 0.0170, -70.60, EstadoAlerta.ACTIVA));  // ~1,9 km
        alertaRepository.save(alerta("norte-fuera", lat + 0.0200, -70.60, EstadoAlerta.ACTIVA));   // ~2,2 km

        List<String> ids = alertaRepository.buscarPorZona(lat, -70.60, 2.0).stream().map(Alerta::getMascotaId).toList();

        assertThat(ids).containsExactlyInAnyOrder("este-dentro", "oeste-dentro", "norte-dentro");
    }

    @Test
    void no_puede_haber_dos_alertas_activas_del_mismo_reporte() {
        alertaRepository.saveAndFlush(alerta("m-dup", -33.44, -70.65, EstadoAlerta.ACTIVA));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> alertaRepository.saveAndFlush(alerta("m-dup", -33.44, -70.65, EstadoAlerta.ACTIVA)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void se_permiten_varias_alertas_inactivas_y_una_nueva_activa_despues_de_desactivar() {
        Alerta primera = alertaRepository.saveAndFlush(alerta("m-ciclo", -33.44, -70.65, EstadoAlerta.ACTIVA));
        primera.setEstado(EstadoAlerta.INACTIVA);
        alertaRepository.saveAndFlush(primera);
        alertaRepository.saveAndFlush(alerta("m-ciclo", -33.44, -70.65, EstadoAlerta.INACTIVA));

        alertaRepository.saveAndFlush(alerta("m-ciclo", -33.44, -70.65, EstadoAlerta.ACTIVA));

        assertThat(alertaRepository.findByMascotaIdAndEstado("m-ciclo", EstadoAlerta.ACTIVA)).hasSize(1);
        assertThat(alertaRepository.findByMascotaIdAndEstado("m-ciclo", EstadoAlerta.INACTIVA)).hasSize(2);
    }
}
