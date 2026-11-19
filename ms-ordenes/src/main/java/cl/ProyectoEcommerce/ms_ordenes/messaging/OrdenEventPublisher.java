package cl.ProyectoEcommerce.ms_ordenes.messaging;

import cl.ProyectoEcommerce.ms_ordenes.events.OrdenCreadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
// "orden.creada" sigue viajando por RabbitMQ porque es una TAREA para ms-productos (descontar stock)
// y ms-notificaciones (enviar correo). La auditoría de ese mismo evento ahora va por Kafka.
public class OrdenEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrdenEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final AuditoriaPublisher auditoriaPublisher;
    private final String exchangeName;
    private final String routingKeyOrdenCreada;

    public OrdenEventPublisher(
            RabbitTemplate rabbitTemplate,
            AuditoriaPublisher auditoriaPublisher,
            @Value("${pedidos360.exchange}") String exchangeName,
            @Value("${pedidos360.routing-key.orden-creada}") String routingKeyOrdenCreada) {
        this.rabbitTemplate = rabbitTemplate;
        this.auditoriaPublisher = auditoriaPublisher;
        this.exchangeName = exchangeName;
        this.routingKeyOrdenCreada = routingKeyOrdenCreada;
    }

    public void publicarOrdenCreada(OrdenCreadaEvent evento) {

        log.info("Publicando orden {} en exchange={} routingKey={}", evento.ordenId(), exchangeName,
                routingKeyOrdenCreada);

        rabbitTemplate.convertAndSend(
            exchangeName,
            routingKeyOrdenCreada,
            evento
        );

        log.info("Evento orden.creada enviado a RabbitMQ: ordenId={}", evento.ordenId());

        // Auditoría por Kafka (antes la cola RabbitMQ de auditoría capturaba este mismo mensaje con "#")
        auditoriaPublisher.publicar(routingKeyOrdenCreada, evento.ordenId(), evento);
    }
}
