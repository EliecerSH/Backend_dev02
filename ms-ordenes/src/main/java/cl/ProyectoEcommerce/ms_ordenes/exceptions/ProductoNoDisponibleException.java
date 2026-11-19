package cl.ProyectoEcommerce.ms_ordenes.exceptions;

public class ProductoNoDisponibleException extends RuntimeException {
    public ProductoNoDisponibleException(Long productoId) {
        super("El producto con ID " + productoId + " ya no existe en el catálogo.");
    }
}
