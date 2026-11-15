package com.sanosysalvos.ms_alertas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanosysalvos.ms_alertas.model.Alerta;
import com.sanosysalvos.ms_alertas.model.EstadoAlerta;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    List<Alerta> findByEstado(EstadoAlerta estado);

    List<Alerta> findByMascotaIdAndEstado(String mascotaId, EstadoAlerta estado);

    // El argumento de acos() se acota a [-1, 1]: por redondeo de punto flotante puede dar
    // 1.0000000000000002 y MySQL devuelve NULL, con lo que una alerta ubicada exactamente
    // en el punto buscado desaparecia del resultado.
    // Fórmula de Haversine: distancia en km entre el centro de cada alerta y el
    // punto buscado. Se filtra en SQL (no en memoria) porque el volumen de
    // alertas activas puede crecer y no hay soporte geoespacial nativo en MySQL
    // estándar (a diferencia de ms-mascotas, que sí usa índices geo de Mongo).
    // La caja de latitud/longitud descarta casi todas las filas con el índice antes de hacer
    // trigonometría fila por fila (el endpoint es público).
    @Query(value = """
            SELECT * FROM alertas a
            WHERE a.estado = 'ACTIVA'
            AND a.latitud_centro BETWEEN :latMin AND :latMax
            AND a.longitud_centro BETWEEN :lngMin AND :lngMax
            AND (6371 * acos(LEAST(1, GREATEST(-1,
                    cos(radians(:lat)) * cos(radians(a.latitud_centro))
                    * cos(radians(a.longitud_centro) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(a.latitud_centro))
                )))) <= :radioKm
            """, nativeQuery = true)
    List<Alerta> buscarPorZonaEnCaja(@Param("lat") Double lat, @Param("lng") Double lng, @Param("radioKm") Double radioKm,
            @Param("latMin") double latMin, @Param("latMax") double latMax,
            @Param("lngMin") double lngMin, @Param("lngMax") double lngMax);

    /**
     * Alertas activas dentro de radioKm. La caja se calcula con 111 km por grado y un 2 % de margen, así que
     * nunca deja fuera un punto que Haversine aceptaría; no cruza el antimeridiano (no aplica a Chile).
     */
    default List<Alerta> buscarPorZona(Double lat, Double lng, Double radioKm) {
        double margen = 1.02;
        double deltaLat = radioKm / 111.0 * margen;
        double cosLat = Math.max(Math.cos(Math.toRadians(lat)), 0.01);
        double deltaLng = radioKm / (111.0 * cosLat) * margen;
        return buscarPorZonaEnCaja(lat, lng, radioKm, lat - deltaLat, lat + deltaLat, lng - deltaLng, lng + deltaLng);
    }
}
