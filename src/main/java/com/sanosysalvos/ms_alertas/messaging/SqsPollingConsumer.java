package com.sanosysalvos.ms_alertas.messaging;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

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

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final boolean autoArranque;

    private Thread hilo;
    private volatile boolean activo;

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
        activo = true;
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
                        .build());

                for (Message mensaje : respuesta.messages()) {
                    procesarYBorrar(mensaje);
                }
            } catch (Exception e) {
                if (activo) {
                    log.error("Error consultando la cola, reintenta en 5s", e);
                    dormir(5000);
                }
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

    protected abstract void procesar(String cuerpoMensaje) throws Exception;
}
