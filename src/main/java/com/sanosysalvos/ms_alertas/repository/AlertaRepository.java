package com.sanosysalvos.ms_alertas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sanosysalvos.ms_alertas.model.Alerta;
import com.sanosysalvos.ms_alertas.model.EstadoAlerta;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    List<Alerta> findByEstado(EstadoAlerta estado);

    // Fórmula de Haversine: distancia en km entre el centro de cada alerta y el
    // punto buscado. Se filtra en SQL (no en memoria) porque el volumen de
    // alertas activas puede crecer y no hay soporte geoespacial nativo en MySQL
    // estándar (a diferencia de ms-mascotas, que sí usa índices geo de Mongo).
    @Query(value = """
            SELECT * FROM alertas a
            WHERE a.estado = 'ACTIVA'
            AND (6371 * acos(
                    cos(radians(:lat)) * cos(radians(a.latitud_centro))
                    * cos(radians(a.longitud_centro) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(a.latitud_centro))
                )) <= :radioKm
            """, nativeQuery = true)
    List<Alerta> buscarPorZona(@Param("lat") Double lat, @Param("lng") Double lng, @Param("radioKm") Double radioKm);
}
