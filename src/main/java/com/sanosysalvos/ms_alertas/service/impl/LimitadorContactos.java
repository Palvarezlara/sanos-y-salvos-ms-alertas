package com.sanosysalvos.ms_alertas.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sanosysalvos.ms_alertas.exception.DemasiadasSolicitudesException;

/**
 * Límite de uso de POST /contactos, que es público (cualquiera que encuentre una mascota puede avisar, sin
 * cuenta). Sin él, un anónimo podía llenar la bandeja de un dueño y agotar la cuota del correo oficial.
 *
 * Ventana deslizante en memoria, por reporte y por correo de quien contacta. Es de una sola instancia: con
 * varias réplicas el tope efectivo se multiplica; el límite por IP/ruta del API Gateway lo complementa.
 */
@Component
public class LimitadorContactos {

    /* tope de claves por correo en memoria: si se llena, solo se aplica el límite por reporte */
    private static final int MAX_CLAVES = 10_000;

    private final int maxPorMascota;
    private final int maxPorCorreo;
    private final Duration ventana;
    private final Clock reloj;
    private final Map<String, Deque<Instant>> intentos = new HashMap<>();

    @Autowired
    public LimitadorContactos(
            @Value("${contacto.limite.max-por-mascota:10}") int maxPorMascota,
            @Value("${contacto.limite.max-por-correo:5}") int maxPorCorreo,
            @Value("${contacto.limite.ventana-minutos:60}") long ventanaMinutos) {
        this(maxPorMascota, maxPorCorreo, Duration.ofMinutes(ventanaMinutos), Clock.systemUTC());
    }

    LimitadorContactos(int maxPorMascota, int maxPorCorreo, Duration ventana, Clock reloj) {
        this.maxPorMascota = maxPorMascota;
        this.maxPorCorreo = maxPorCorreo;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    /** Registra un intento o lanza DemasiadasSolicitudesException; los intentos rechazados no cuentan. */
    public synchronized void registrar(String mascotaId, String correoContacto) {
        Instant ahora = reloj.instant();
        if (intentos.size() > MAX_CLAVES) {
            purgar(ahora);
        }
        String claveMascota = "m:" + mascotaId;
        String claveCorreo = correoContacto == null ? null : "c:" + correoContacto.strip().toLowerCase(Locale.ROOT);
        boolean seguirCorreo = claveCorreo != null && (intentos.containsKey(claveCorreo) || intentos.size() < MAX_CLAVES);

        long espera = Math.max(espera(claveMascota, maxPorMascota, ahora),
                seguirCorreo ? espera(claveCorreo, maxPorCorreo, ahora) : 0);
        if (espera > 0) {
            throw new DemasiadasSolicitudesException(espera);
        }
        intentos.computeIfAbsent(claveMascota, k -> new ArrayDeque<>()).addLast(ahora);
        if (seguirCorreo) {
            intentos.computeIfAbsent(claveCorreo, k -> new ArrayDeque<>()).addLast(ahora);
        }
    }

    /* segundos hasta que se libere un cupo, o 0 si todavía hay */
    private long espera(String clave, int maximo, Instant ahora) {
        Deque<Instant> usados = intentos.get(clave);
        if (usados == null) {
            return 0;
        }
        while (!usados.isEmpty() && usados.peekFirst().plus(ventana).isBefore(ahora)) {
            usados.removeFirst();
        }
        if (usados.size() < maximo) {
            return 0;
        }
        return Math.max(1, Duration.between(ahora, usados.peekFirst().plus(ventana)).toSeconds() + 1);
    }

    private void purgar(Instant ahora) {
        intentos.values().removeIf(usados -> usados.isEmpty() || usados.peekLast().plus(ventana).isBefore(ahora));
    }
}
