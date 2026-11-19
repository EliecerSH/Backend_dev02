package cl.ProyectoEcommerce.ms_ordenes.exceptions;

public class OrdenNoEliminableException extends RuntimeException {
    public OrdenNoEliminableException(Long id, String estado) {
        super("El pedido #" + id + " no se puede eliminar porque está en estado " + estado
                + ". Solo se eliminan pedidos pendientes de pago o con pago rechazado.");
    }
}
