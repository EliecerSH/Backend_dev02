package cl.ProyectoEcommerce.ms_ordenes.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrdenNotFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarOrdenNoEncontrada(OrdenNotFoundException ex) {
        return construirRespuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Datos inválidos");
        return construirRespuesta(HttpStatus.BAD_REQUEST, mensaje);
    }

    // Sin este handler, el "No tienes permiso" del controller (y los rechazos de @PreAuthorize)
    // caían en el handler genérico de RuntimeException y respondían 500 en vez de 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> manejarAccesoDenegado(AccessDeniedException ex) {
        return construirRespuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(OrdenNoEliminableException.class)
    public ResponseEntity<Map<String, Object>> manejarNoEliminable(OrdenNoEliminableException ex) {
        return construirRespuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<Map<String, Object>> manejarStock(StockInsuficienteException ex) {
        return construirRespuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ProductoNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> manejarProducto(ProductoNoDisponibleException ex) {
        return construirRespuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CatalogoNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> manejarCatalogo(CatalogoNoDisponibleException ex) {
        return construirRespuesta(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> manejarGenerico(RuntimeException ex) {
        return construirRespuesta(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> construirRespuesta(HttpStatus status, String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("mensaje", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}
