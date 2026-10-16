package com.sanosysalvos.ms_alertas.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cola de correo de contacto: ContactoServiceImpl publica acá en vez de
 * llamar a JavaMailSender directo, para no bloquear la respuesta al usuario
 * mientras se manda el correo. ContactoMailListener consume y recién ahí
 * envía.
 *
 * Si el envío falla después de los reintentos configurados (ver
 * spring.rabbitmq.listener.simple.retry en ms-alertas.yml), el mensaje cae
 * en COLA_FALLIDOS en vez de perderse.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "sanos.eventos";
    public static final String COLA_CONTACTO = "cola.contacto";
    public static final String ROUTING_KEY_CONTACTO = "contacto.solicitado";

    private static final String DLX = "sanos.eventos.dlx";
    private static final String COLA_FALLIDOS = "cola.contacto.fallidos";

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
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

    @Bean
    Queue colaContacto() {
        return QueueBuilder.durable(COLA_CONTACTO)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_CONTACTO)
                .build();
    }

    @Bean
    Queue colaContactoFallidos() {
        return QueueBuilder.durable(COLA_FALLIDOS).build();
    }

    @Bean
    Binding bindingContacto(Queue colaContacto, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaContacto).to(eventosExchange).with(ROUTING_KEY_CONTACTO);
    }

    @Bean
    Binding bindingContactoFallidos(Queue colaContactoFallidos, TopicExchange eventosDlx) {
        return BindingBuilder.bind(colaContactoFallidos).to(eventosDlx).with(ROUTING_KEY_CONTACTO);
    }
}
