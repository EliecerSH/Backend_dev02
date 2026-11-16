package cl.ProyectoEcommerce.ms_ordenes.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

// Evento publicado por ms-pagos en el tópico Kafka "pagos.resultados" (key = orderId).
// status: AUTHORIZED | REJECTED | CANCELLED | ERROR
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoResultadoEvent(
        String eventId,
        Long orderId,
        String clientId,
        String status,
        BigDecimal amount,
        String authorizationCode,
        Instant occurredAt) {
}
