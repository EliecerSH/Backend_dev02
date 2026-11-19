package cl.ProyectoEcommerce.ms_ordenes.exceptions;

public class CatalogoNoDisponibleException extends RuntimeException {
    public CatalogoNoDisponibleException() {
        super("No se pudo validar el catálogo de productos. Intenta nuevamente en unos segundos.");
    }
}
