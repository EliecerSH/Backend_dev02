package cl.ProyectoEcommerce.ms_ordenes.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Ítem del carrito enviado por el frontend al momento de "Finalizar compra".
// precioUnitario se acepta por compatibilidad pero se IGNORA: el precio real se obtiene de ms-productos.
public record ItemOrdenRequestDTO(
        @NotNull(message = "El ID del producto es obligatorio") Long productoId,

        @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad debe ser al menos 1") @Max(value = 100, message = "La cantidad máxima por producto es 100") Integer cantidad,

        BigDecimal precioUnitario) {
}
