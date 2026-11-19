package cl.proyectoEcommerce.ms_auditoria.messaging;

import cl.proyectoEcommerce.ms_auditoria.models.RegistroAuditoria;
import cl.proyectoEcommerce.ms_auditoria.repositories.RegistroAuditoriaRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class EventoListener {

    private static final Logger log = LoggerFactory.getLogger(EventoListener.class);

    static final String HEADER_TIPO = "tipo-evento";
    static final String HEADER_EVENT_ID = "event-id";
    static final String HEADER_ORIGEN = "origen";

    private final RegistroAuditoriaRepository registroAuditoriaRepository;

    public EventoListener(RegistroAuditoriaRepository registroAuditoriaRepository) {
        this.registroAuditoriaRepository = registroAuditoriaRepository;
    }

    // Consume el tópico Kafka "auditoria.eventos", donde ms-ordenes, ms-productos y ms-pagos
    // publican cada hito del negocio. Reemplaza a la cola RabbitMQ pedidos360.auditoria.queue
    // (binding "#"): el tipo de evento viaja en el header "tipo-evento" con el mismo nombre que
    // antes tenía la routing key, y el payload es el JSON original del evento, sin acoplarse a un DTO.
    // Kafka retiene los eventos, así que si ms-auditoria estuvo caído, al volver procesa lo pendiente.
    @KafkaListener(topics = "${auditoria.kafka.topic}")
    public void auditarEvento(ConsumerRecord<String, String> registro) {
        String tipoEvento = header(registro, HEADER_TIPO);
        if (tipoEvento == null || tipoEvento.isBlank()) {
            tipoEvento = registro.topic();
        }
        String eventId = header(registro, HEADER_EVENT_ID);
        String origen = header(registro, HEADER_ORIGEN);
        String payload = registro.value() == null ? "" : registro.value();

        // Idempotencia: si Kafka re-entrega el mismo mensaje, no se duplica el registro
        if (eventId != null && registroAuditoriaRepository.existsByEventId(eventId)) {
            log.debug("[AUDITORÍA] Evento duplicado ignorado: {}", eventId);
            return;
        }
        try {
            registroAuditoriaRepository.save(new RegistroAuditoria(tipoEvento, payload, eventId, origen));
            log.info("[AUDITORÍA] Evento '{}' de {} registrado (partition={}, offset={})",
                    tipoEvento, origen, registro.partition(), registro.offset());
        } catch (DataIntegrityViolationException duplicado) {
            log.debug("[AUDITORÍA] Evento duplicado ignorado: {}", eventId);
        }
    }

    private static String header(ConsumerRecord<String, String> registro, String nombre) {
        Header h = registro.headers().lastHeader(nombre);
        return h == null || h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
