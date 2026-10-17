package com.sanosysalvos.ms_alertas.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchange compartido de eventos del sistema ("sanos.eventos"), con una cola
 * por tipo de evento que consume este servicio:
 *
 * - cola.contacto: ContactoServiceImpl publica en vez de llamar a
 *   JavaMailSender directo, para no bloquear la respuesta al usuario
 *   mientras se manda el correo. ContactoMailListener consume y recién ahí
 *   envía.
 * - cola.mascota.reportada: ms-mascotas publica cuando se crea una mascota
 *   con ubicación; MascotaReportadaListener consume y crea la Alerta
 *   geográfica correspondiente.
 *
 * Si el procesamiento falla después de los reintentos configurados (ver
 * spring.rabbitmq.listener.simple.retry en ms-alertas.yml), el mensaje cae
 * en su cola de fallidos correspondiente en vez de perderse.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "sanos.eventos";

    public static final String COLA_CONTACTO = "cola.contacto";
    public static final String ROUTING_KEY_CONTACTO = "contacto.solicitado";

    public static final String COLA_MASCOTA_REPORTADA = "cola.mascota.reportada";
    public static final String ROUTING_KEY_MASCOTA_REPORTADA = "mascota.reportada";

    private static final String DLX = "sanos.eventos.dlx";
    private static final String COLA_CONTACTO_FALLIDOS = "cola.contacto.fallidos";
    private static final String COLA_MASCOTA_REPORTADA_FALLIDOS = "cola.mascota.reportada.fallidos";

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    TopicExchange eventosDlx() {
        return new TopicExchange(DLX);
    }

    // --- contacto ---

    @Bean
    Queue colaContacto() {
        return QueueBuilder.durable(COLA_CONTACTO)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_CONTACTO)
                .build();
    }

    @Bean
    Queue colaContactoFallidos() {
        return QueueBuilder.durable(COLA_CONTACTO_FALLIDOS).build();
    }

    @Bean
    Binding bindingContacto(Queue colaContacto, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaContacto).to(eventosExchange).with(ROUTING_KEY_CONTACTO);
    }

    @Bean
    Binding bindingContactoFallidos(Queue colaContactoFallidos, TopicExchange eventosDlx) {
        return BindingBuilder.bind(colaContactoFallidos).to(eventosDlx).with(ROUTING_KEY_CONTACTO);
    }

    // --- mascota reportada ---

    @Bean
    Queue colaMascotaReportada() {
        return QueueBuilder.durable(COLA_MASCOTA_REPORTADA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_MASCOTA_REPORTADA)
                .build();
    }

    @Bean
    Queue colaMascotaReportadaFallidos() {
        return QueueBuilder.durable(COLA_MASCOTA_REPORTADA_FALLIDOS).build();
    }

    @Bean
    Binding bindingMascotaReportada(Queue colaMascotaReportada, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaMascotaReportada).to(eventosExchange).with(ROUTING_KEY_MASCOTA_REPORTADA);
    }

    @Bean
    Binding bindingMascotaReportadaFallidos(Queue colaMascotaReportadaFallidos, TopicExchange eventosDlx) {
        return BindingBuilder.bind(colaMascotaReportadaFallidos).to(eventosDlx)
                .with(ROUTING_KEY_MASCOTA_REPORTADA);
    }
}
