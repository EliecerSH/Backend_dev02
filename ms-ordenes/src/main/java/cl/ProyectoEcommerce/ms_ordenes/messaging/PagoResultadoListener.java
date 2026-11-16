package cl.ProyectoEcommerce.ms_ordenes.messaging;

import cl.ProyectoEcommerce.ms_ordenes.events.PagoResultadoEvent;
import cl.ProyectoEcommerce.ms_ordenes.services.OrdenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PagoResultadoListener {

    private static final Logger log = LoggerFactory.getLogger(PagoResultadoListener.class);
    private final OrdenService ordenService;

    public PagoResultadoListener(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    // Solo se activa cuando PAGO_REQUERIDO=true (sin Kafka configurado el servicio funciona como antes)
    @KafkaListener(topics = "${pedidos360.pagos.topic-resultados:pagos.resultados}",
            autoStartup = "${pedidos360.pagos.requerido:false}")
    public void onPagoResultado(PagoResultadoEvent evento) {
        log.info("Resultado de pago recibido desde Kafka: orden={} estado={}", evento.orderId(), evento.status());
        ordenService.aplicarResultadoPago(evento);
    }
}
