package com.sanosysalvos.ms_alertas.messaging;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Reemplaza a @RabbitListener: un hilo propio por cola, con long polling
 * (espera hasta 10s por mensajes nuevos en vez de consultar en loop
 * cerrado). Si procesar() lanza una excepción, el mensaje NO se borra —
 * vuelve a quedar visible pasado el tiempo de visibilidad de la cola, y SQS
 * lo manda solo a la cola de fallidos (redrive policy) después de
 * maxReceiveCount intentos — no hace falta armar esa lógica a mano, como sí
 * había que hacer con la dead-letter exchange de RabbitMQ.
 */
public abstract class SqsPollingConsumer {

    private static final Logger log = LoggerFactory.getLogger(SqsPollingConsumer.class);

    private static final int VISIBILIDAD_SEGUNDOS = 120;

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final boolean autoArranque;

    private Thread hilo;
    private volatile boolean activo;
    private volatile boolean iniciado;
    private volatile Instant ultimoSondeoExitoso = Instant.EPOCH;

    protected SqsPollingConsumer(SqsClient sqsClient, String queueUrl, boolean autoArranque) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.autoArranque = autoArranque;
    }

    @PostConstruct
    void iniciar() {
        if (!autoArranque) {
            // Se desactiva en el perfil de test — sin esto, cada test
            // levantaría un hilo real intentando conectarse a una cola.
            return;
        }
        if (queueUrl == null || queueUrl.isBlank()) {
            // cola opcional (ej. Terraform todavia no la define): ese consumidor queda apagado, el resto sigue
            log.warn("{} desactivado: no hay URL de cola configurada", getClass().getSimpleName());
            return;
        }
        activo = true;
        iniciado = true;
        ultimoSondeoExitoso = Instant.now();
        hilo = new Thread(this::escuchar, getClass().getSimpleName());
        hilo.setDaemon(true);
        hilo.start();
    }

    @PreDestroy
    void detener() {
        activo = false;
        if (hilo != null) {
            hilo.interrupt();
        }
    }

    private void escuchar() {
        while (activo) {
            try {
                var respuesta = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(10)
                        // el lote se procesa en serie: si el SMTP es lento, los ultimos mensajes
                        // reaparecerian en la cola (y el dueño recibiria el correo dos veces)
                        // antes de que les toque el turno
                        .visibilityTimeout(VISIBILIDAD_SEGUNDOS)
                        .build());

                ultimoSondeoExitoso = Instant.now();

                for (Message mensaje : respuesta.messages()) {
                    procesarYBorrar(mensaje);
                }
            } catch (Exception e) {
                if (activo) {
                    log.error("Error consultando la cola, reintenta en 5s", e);
                    dormir(5000);
                }
            } catch (Error e) {
                // OutOfMemoryError y similares: el hilo muere, pero ya no en silencio (el health pasa a DOWN)
                activo = false;
                log.error("Consumidor SQS detenido por un error fatal", e);
                throw e;
            }
        }
    }

    private void procesarYBorrar(Message mensaje) {
        try {
            procesar(mensaje.body());
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(mensaje.receiptHandle())
                    .build());
        } catch (Exception e) {
            log.error("No se pudo procesar el mensaje, queda en la cola para reintento", e);
        }
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** true si este consumidor arrancó (cola configurada y autoarranque activo). */
    public boolean estaIniciado() {
        return iniciado;
    }

    /** Hilo vivo y con un sondeo exitoso reciente; falla con una URL incorrecta, sin permisos o sin red. */
    public boolean estaSano(Duration tolerancia) {
        return hilo != null && hilo.isAlive() && ultimoSondeoExitoso.plus(tolerancia).isAfter(Instant.now());
    }

    public String nombre() {
        return getClass().getSimpleName();
    }

    protected abstract void procesar(String cuerpoMensaje) throws Exception;
}
