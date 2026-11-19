package cl.ProyectoEcommerce.ms_ordenes.events;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Evento publicado a RabbitMQ cuando la orden queda confirmada (pagada, o creada si no se exige pago).
// Lo consumen: ms-productos (descuenta stock) y ms-notificaciones (envía correo).
// ms-auditoria lo recibe por Kafka (tópico auditoria.eventos).
public record OrdenCreadaEvent(
        Long ordenId,
        String usuarioOid,
        List<ItemEvento> items,
        BigDecimal total,
        LocalDateTime fecha) implements Serializable {
}
