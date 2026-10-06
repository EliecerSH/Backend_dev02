package cl.ProyectoEcommerce.ms_ordenes.controllers;

import cl.ProyectoEcommerce.ms_ordenes.dto.CrearOrdenRequestDTO;
import cl.ProyectoEcommerce.ms_ordenes.dto.OrdenResponseDTO;
import cl.ProyectoEcommerce.ms_ordenes.services.OrdenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ordenes")
@PreAuthorize("hasAnyRole('Admin', 'User')")
public class OrdenController {

    private static final String ROL_ADMIN = "ROLE_Admin";

    private final OrdenService ordenService;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    @PostMapping
    public ResponseEntity<OrdenResponseDTO> crearOrden(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CrearOrdenRequestDTO request) {
        String usuarioOid = extraerUsuarioOid(jwt);
        OrdenResponseDTO orden = ordenService.crearOrden(usuarioOid, request);
        return new ResponseEntity<>(orden, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<OrdenResponseDTO>> obtenerMisOrdenes(@AuthenticationPrincipal Jwt jwt) {
        String usuarioOid = extraerUsuarioOid(jwt);
        return ResponseEntity.ok(ordenService.obtenerOrdenesDeUsuario(usuarioOid));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdenResponseDTO> obtenerPorId(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable Long id) {

        OrdenResponseDTO orden = ordenService.obtenerPorId(id);

        boolean esDueño = extraerUsuarioOid(jwt).equals(orden.usuarioOid());

        if (!esDueño && !esAdmin(authentication)) {
            throw new AccessDeniedException("No tienes permiso para ver esta orden.");
        }
        return ResponseEntity.ok(orden);
    }

    private boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(ROL_ADMIN));
    }

    private String extraerUsuarioOid(Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        if (oid != null && !oid.isBlank()) {
            return oid;
        }
        return jwt.getSubject();
    }
}