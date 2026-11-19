package cl.proyectoEcommerce.ms_auditoria.models;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "registros_auditoria",
        uniqueConstraints = @UniqueConstraint(name = "uk_auditoria_event_id", columnNames = "event_id"))
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tipo del evento (header "tipo-evento" de Kafka), ej: "pedidos360.orden.creada", "pedidos360.pago.authorized"
    @Column(name = "tipo_evento", nullable = false, length = 100)
    private String tipoEvento;

    // Contenido íntegro del evento en JSON, tal como fue publicado por el microservicio origen
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "recibido_en", updatable = false)
    private LocalDateTime recibidoEn;

    // Identificador único del evento (header "event-id"): evita registrar dos veces un mensaje re-entregado
    @Column(name = "event_id", length = 64)
    private String eventId;

    // Microservicio que generó el evento (header "origen")
    @Column(length = 50)
    private String origen;

    public RegistroAuditoria() {
    }

    public RegistroAuditoria(String tipoEvento, String payload) {
        this.tipoEvento = tipoEvento;
        this.payload = payload;
    }

    public RegistroAuditoria(String tipoEvento, String payload, String eventId, String origen) {
        this(tipoEvento, payload);
        this.eventId = eventId;
        this.origen = origen;
    }

    @PrePersist
    protected void onCreate() {
        this.recibidoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTipoEvento() { return tipoEvento; }
    public void setTipoEvento(String tipoEvento) { this.tipoEvento = tipoEvento; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public LocalDateTime getRecibidoEn() { return recibidoEn; }

    public String getEventId() { return eventId; }

    public String getOrigen() { return origen; }
}
