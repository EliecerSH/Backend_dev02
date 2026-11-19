package cl.ProyectoEcommerce.ms_ordenes.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Payload de auditoría de los cambios de estado de una orden (tópico Kafka auditoria.eventos)
public record OrdenAuditoriaEvent(
        Long ordenId,
        String usuarioOid,
        String estado,
        BigDecimal total,
        List<ItemEvento> items,
        String detalle,
        LocalDateTime fecha) {
}
