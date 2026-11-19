package cl.ProyectoEcommerce.ms_ordenes.exceptions;

public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String nombre, int disponible, int solicitado) {
        super("Stock insuficiente para \"" + nombre + "\": disponible " + disponible + ", solicitado " + solicitado + ".");
    }
}
