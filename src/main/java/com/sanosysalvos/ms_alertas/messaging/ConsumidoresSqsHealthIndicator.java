package com.sanosysalvos.ms_alertas.messaging;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Señal de salud de los consumidores SQS: sin ella, un hilo muerto o una URL de cola incorrecta dejaban
 * /actuator/health en UP mientras nadie procesaba mensajes (alertas y correos se perdían sin aviso).
 */
@Component("consumidoresSqs")
public class ConsumidoresSqsHealthIndicator implements HealthIndicator {

    /* un sondeo con long polling dura hasta 10 s; con 90 s sin ninguno exitoso el consumidor está caído */
    static final Duration TOLERANCIA = Duration.ofSeconds(90);

    private final List<SqsPollingConsumer> consumidores;

    public ConsumidoresSqsHealthIndicator(List<SqsPollingConsumer> consumidores) {
        this.consumidores = consumidores;
    }

    @Override
    public Health health() {
        List<String> caidos = consumidores.stream()
                .filter(SqsPollingConsumer::estaIniciado)
                .filter(c -> !c.estaSano(TOLERANCIA))
                .map(SqsPollingConsumer::nombre)
                .collect(Collectors.toList());
        if (caidos.isEmpty()) {
            return Health.up().build();
        }
        return Health.down().withDetail("consumidoresCaidos", caidos).build();
    }
}
