package cl.proyectoEcommerce.ms_auditoria.repositories;

import cl.proyectoEcommerce.ms_auditoria.models.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    boolean existsByEventId(String eventId);

    // Los 500 eventos más recientes (antes se cargaba la tabla completa en memoria)
    List<RegistroAuditoria> findTop500ByOrderByRecibidoEnDesc();
}
