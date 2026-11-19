package cl.ProyectoEcommerce.ms_ordenes.services;

import cl.ProyectoEcommerce.ms_ordenes.clients.ProductoCatalogoClient;
import cl.ProyectoEcommerce.ms_ordenes.clients.ProductoCatalogoClient.ProductoCatalogo;
import cl.ProyectoEcommerce.ms_ordenes.dto.*;
import cl.ProyectoEcommerce.ms_ordenes.events.ItemEvento;
import cl.ProyectoEcommerce.ms_ordenes.events.OrdenAuditoriaEvent;
import cl.ProyectoEcommerce.ms_ordenes.events.OrdenCreadaEvent;
import cl.ProyectoEcommerce.ms_ordenes.events.PagoResultadoEvent;
import cl.ProyectoEcommerce.ms_ordenes.exceptions.OrdenNoEliminableException;
import cl.ProyectoEcommerce.ms_ordenes.exceptions.OrdenNotFoundException;
import cl.ProyectoEcommerce.ms_ordenes.exceptions.StockInsuficienteException;
import cl.ProyectoEcommerce.ms_ordenes.messaging.AuditoriaPublisher;
import cl.ProyectoEcommerce.ms_ordenes.messaging.OrdenEventPublisher;
import cl.ProyectoEcommerce.ms_ordenes.models.Orden;
import cl.ProyectoEcommerce.ms_ordenes.models.OrdenItem;
import cl.ProyectoEcommerce.ms_ordenes.repositories.OrdenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrdenService {

        public static final String ESTADO_PENDIENTE_PAGO = "PENDIENTE_PAGO";
        public static final String ESTADO_PAGADA = "PAGADA";
        public static final String ESTADO_PAGO_RECHAZADO = "PAGO_RECHAZADO";
        public static final String ESTADO_PROCESADA = "PROCESADA";

        // Tipos de evento de auditoría (tópico Kafka auditoria.eventos)
        static final String AUDIT_ORDEN_REGISTRADA = "pedidos360.orden.registrada";
        static final String AUDIT_ORDEN_PAGADA = "pedidos360.orden.pagada";
        static final String AUDIT_ORDEN_PAGO_RECHAZADO = "pedidos360.orden.pago-rechazado";
        static final String AUDIT_ORDEN_ELIMINADA = "pedidos360.orden.eliminada";
        static final String AUDIT_PAGO_SIN_ORDEN = "pedidos360.orden.pago-sin-orden";

        // Solo se pueden eliminar pedidos que todavía no se pagaron
        private static final java.util.Set<String> ESTADOS_ELIMINABLES =
                        java.util.Set.of(ESTADO_PENDIENTE_PAGO, ESTADO_PAGO_RECHAZADO);

        private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

        private final OrdenRepository ordenRepository;
        private final OrdenEventPublisher ordenEventPublisher;
        private final AuditoriaPublisher auditoriaPublisher;
        private final ProductoCatalogoClient catalogoClient;
        private final boolean pagoRequerido;

        public OrdenService(OrdenRepository ordenRepository, OrdenEventPublisher ordenEventPublisher,
                        AuditoriaPublisher auditoriaPublisher, ProductoCatalogoClient catalogoClient,
                        @Value("${pedidos360.pagos.requerido:false}") boolean pagoRequerido) {
                this.ordenRepository = ordenRepository;
                this.ordenEventPublisher = ordenEventPublisher;
                this.auditoriaPublisher = auditoriaPublisher;
                this.catalogoClient = catalogoClient;
                this.pagoRequerido = pagoRequerido;
        }

        // Registra la orden a partir de los productos y cantidades del carrito.
        // SEGURIDAD: el precio de cada ítem se toma del catálogo de ms-productos, nunca del navegador
        // (antes se usaba el precioUnitario enviado por el frontend y se podía manipular el monto a pagar).
        // También se valida que haya stock suficiente y se unifican ítems repetidos del mismo producto.
        // - Con pago requerido (PAGO_REQUERIDO=true): la orden queda PENDIENTE_PAGO y NO se publica
        //   "orden.creada" todavía; se publica cuando ms-pagos informa AUTHORIZED por Kafka.
        // - Sin pago requerido: comportamiento anterior (PROCESADA + "orden.creada" inmediato).
        @Transactional
        public OrdenResponseDTO crearOrden(String usuarioOid, CrearOrdenRequestDTO request) {
                Map<Long, Integer> cantidades = new LinkedHashMap<>();
                request.items().forEach(item -> cantidades.merge(item.productoId(), item.cantidad(), Integer::sum));

                Orden orden = new Orden(usuarioOid, BigDecimal.ZERO);
                BigDecimal total = BigDecimal.ZERO;
                for (Map.Entry<Long, Integer> entrada : cantidades.entrySet()) {
                        ProductoCatalogo producto = catalogoClient.obtener(entrada.getKey());
                        int cantidad = entrada.getValue();
                        int stock = producto.stock() == null ? 0 : producto.stock();
                        if (stock < cantidad) {
                                throw new StockInsuficienteException(producto.nombre(), stock, cantidad);
                        }
                        orden.agregarItem(new OrdenItem(producto.id(), cantidad, producto.precio()));
                        total = total.add(producto.precio().multiply(BigDecimal.valueOf(cantidad)));
                }
                orden.setTotal(total);
                orden.setEstado(pagoRequerido ? ESTADO_PENDIENTE_PAGO : ESTADO_PROCESADA);

                Orden ordenGuardada = ordenRepository.save(orden);

                auditar(AUDIT_ORDEN_REGISTRADA, ordenGuardada, null);
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
                        if ("AUTHORIZED".equals(evento.status())) {
                                // Caso límite: el cliente eliminó el pedido mientras tenía Webpay abierto y luego pagó.
                                // Se deja registro en auditoría para revisar y reembolsar manualmente.
                                log.error("Pago AUTHORIZED para la orden {} que ya no existe (cliente {}). Requiere reembolso manual.",
                                                evento.orderId(), evento.clientId());
                                auditoriaPublisher.publicar(AUDIT_PAGO_SIN_ORDEN, evento.orderId(), evento);
                        } else {
                                log.warn("Resultado de pago para una orden inexistente: {}", evento.orderId());
                        }
                        return;
                }
                if (ESTADO_PAGADA.equals(orden.getEstado())) {
                        return;
                }
                if ("AUTHORIZED".equals(evento.status())) {
                        orden.setEstado(ESTADO_PAGADA);
                        ordenRepository.save(orden);
                        // Recién ahora se dispara el flujo RabbitMQ: descuento de stock y correo
                        publicarOrdenCreada(orden);
                        auditar(AUDIT_ORDEN_PAGADA, orden, "Autorización " + evento.authorizationCode());
                        log.info("Orden {} pagada (autorización {})", orden.getId(), evento.authorizationCode());
                } else {
                        orden.setEstado(ESTADO_PAGO_RECHAZADO);
                        ordenRepository.save(orden);
                        auditar(AUDIT_ORDEN_PAGO_RECHAZADO, orden, "Resultado Webpay: " + evento.status());
                        log.info("Pago de la orden {} no autorizado: {}", orden.getId(), evento.status());
                }
        }

        private void publicarOrdenCreada(Orden orden) {
                ordenEventPublisher.publicarOrdenCreada(new OrdenCreadaEvent(
                                orden.getId(),
                                orden.getUsuarioOid(),
                                itemsEvento(orden),
                                orden.getTotal(),
                                LocalDateTime.now()));
        }

        private void auditar(String tipo, Orden orden, String detalle) {
                auditoriaPublisher.publicar(tipo, orden.getId(), new OrdenAuditoriaEvent(
                                orden.getId(),
                                orden.getUsuarioOid(),
                                orden.getEstado(),
                                orden.getTotal(),
                                itemsEvento(orden),
                                detalle,
                                LocalDateTime.now()));
        }

        private static List<ItemEvento> itemsEvento(Orden orden) {
                return orden.getItems().stream()
                                .map(i -> new ItemEvento(i.getProductoId(), i.getCantidad(), i.getPrecioUnitario()))
                                .toList();
        }

        // Elimina un pedido no pagado. Solo el dueño o un Admin.
        @Transactional
        public void eliminarOrden(Long id, String usuarioOid, boolean esAdmin) {
                Orden orden = ordenRepository.findById(id)
                                .orElseThrow(() -> new OrdenNotFoundException(id));
                if (!esAdmin && !orden.getUsuarioOid().equals(usuarioOid)) {
                        throw new AccessDeniedException("No tienes permiso para eliminar este pedido.");
                }
                if (!ESTADOS_ELIMINABLES.contains(orden.getEstado())) {
                        throw new OrdenNoEliminableException(id, orden.getEstado());
                }
                auditar(AUDIT_ORDEN_ELIMINADA, orden, "Eliminado por " + (esAdmin ? "Admin" : "el cliente"));
                ordenRepository.delete(orden);
                log.info("Orden {} eliminada (estado {})", id, orden.getEstado());
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
