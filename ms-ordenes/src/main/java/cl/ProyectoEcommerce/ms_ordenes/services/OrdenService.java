package cl.ProyectoEcommerce.ms_ordenes.services;

import cl.ProyectoEcommerce.ms_ordenes.dto.*;
import cl.ProyectoEcommerce.ms_ordenes.events.ItemEvento;
import cl.ProyectoEcommerce.ms_ordenes.events.OrdenCreadaEvent;
import cl.ProyectoEcommerce.ms_ordenes.events.PagoResultadoEvent;
import cl.ProyectoEcommerce.ms_ordenes.exceptions.OrdenNotFoundException;
import cl.ProyectoEcommerce.ms_ordenes.messaging.OrdenEventPublisher;
import cl.ProyectoEcommerce.ms_ordenes.models.Orden;
import cl.ProyectoEcommerce.ms_ordenes.models.OrdenItem;
import cl.ProyectoEcommerce.ms_ordenes.repositories.OrdenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrdenService {

        public static final String ESTADO_PENDIENTE_PAGO = "PENDIENTE_PAGO";
        public static final String ESTADO_PAGADA = "PAGADA";
        public static final String ESTADO_PAGO_RECHAZADO = "PAGO_RECHAZADO";
        public static final String ESTADO_PROCESADA = "PROCESADA";

        private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

        private final OrdenRepository ordenRepository;
        private final OrdenEventPublisher ordenEventPublisher;
        private final boolean pagoRequerido;

        public OrdenService(OrdenRepository ordenRepository, OrdenEventPublisher ordenEventPublisher,
                        @Value("${pedidos360.pagos.requerido:false}") boolean pagoRequerido) {
                this.ordenRepository = ordenRepository;
                this.ordenEventPublisher = ordenEventPublisher;
                this.pagoRequerido = pagoRequerido;
        }

        // Registra la orden a partir del snapshot del carrito recibido desde el frontend.
        // - Con pago requerido (PAGO_REQUERIDO=true): la orden queda PENDIENTE_PAGO y NO se publica
        //   "orden.creada" todavía; se publica cuando ms-pagos informa AUTHORIZED por Kafka
        //   (así no se descuenta stock ni se envía correo de una compra que no se pagó).
        // - Sin pago requerido: comportamiento anterior (PROCESADA + "orden.creada" inmediato).
        @Transactional
        public OrdenResponseDTO crearOrden(String usuarioOid, CrearOrdenRequestDTO request) {
                BigDecimal total = request.items().stream()
                                .map(item -> item.precioUnitario().multiply(BigDecimal.valueOf(item.cantidad())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                Orden orden = new Orden(usuarioOid, total);
                request.items().forEach(item -> orden
                                .agregarItem(new OrdenItem(item.productoId(), item.cantidad(), item.precioUnitario())));
                orden.setEstado(pagoRequerido ? ESTADO_PENDIENTE_PAGO : ESTADO_PROCESADA);

                Orden ordenGuardada = ordenRepository.save(orden);

                if (!pagoRequerido) {
                        publicarOrdenCreada(ordenGuardada);
                }
                return mapToResponseDTO(ordenGuardada);
        }

        // Aplica el resultado informado por ms-pagos (tópico Kafka pagos.resultados). Idempotente:
        // si el evento llega repetido, una orden ya PAGADA no se vuelve a publicar.
        @Transactional
        public void aplicarResultadoPago(PagoResultadoEvent evento) {
                if (evento == null || evento.orderId() == null) {
                        return;
                }
                Orden orden = ordenRepository.findById(evento.orderId()).orElse(null);
                if (orden == null) {
                        log.warn("Resultado de pago para una orden inexistente: {}", evento.orderId());
                        return;
                }
                if (ESTADO_PAGADA.equals(orden.getEstado())) {
                        return;
                }
                if ("AUTHORIZED".equals(evento.status())) {
                        orden.setEstado(ESTADO_PAGADA);
                        ordenRepository.save(orden);
                        // Recién ahora se dispara el flujo RabbitMQ existente: stock, correo y auditoría
                        publicarOrdenCreada(orden);
                        log.info("Orden {} pagada (autorización {})", orden.getId(), evento.authorizationCode());
                } else {
                        orden.setEstado(ESTADO_PAGO_RECHAZADO);
                        ordenRepository.save(orden);
                        log.info("Pago de la orden {} no autorizado: {}", orden.getId(), evento.status());
                }
        }

        private void publicarOrdenCreada(Orden orden) {
                List<ItemEvento> itemsEvento = orden.getItems().stream()
                                .map(i -> new ItemEvento(i.getProductoId(), i.getCantidad(), i.getPrecioUnitario()))
                                .toList();

                ordenEventPublisher.publicarOrdenCreada(new OrdenCreadaEvent(
                                orden.getId(),
                                orden.getUsuarioOid(),
                                itemsEvento,
                                orden.getTotal(),
                                LocalDateTime.now()));
        }

        @Transactional(readOnly = true)
        public List<OrdenResponseDTO> obtenerOrdenesDeUsuario(String usuarioOid) {
                return ordenRepository.findByUsuarioOidOrderByCreadoEnDesc(usuarioOid).stream()
                                .map(this::mapToResponseDTO)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public OrdenResponseDTO obtenerPorId(Long id) {
                Orden orden = ordenRepository.findById(id)
                                .orElseThrow(() -> new OrdenNotFoundException(id));
                return mapToResponseDTO(orden);
        }

        private OrdenResponseDTO mapToResponseDTO(Orden orden) {
                List<ItemOrdenResponseDTO> items = orden.getItems().stream()
                                .map(i -> new ItemOrdenResponseDTO(i.getProductoId(), i.getCantidad(),
                                                i.getPrecioUnitario(), i.getSubtotal()))
                                .toList();

                return new OrdenResponseDTO(
                                orden.getId(),
                                orden.getUsuarioOid(),
                                items,
                                orden.getTotal(),
                                orden.getEstado(),
                                orden.getCreadoEn());
        }
}
