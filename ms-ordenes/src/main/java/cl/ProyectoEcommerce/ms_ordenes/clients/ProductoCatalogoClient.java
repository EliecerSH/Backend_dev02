package cl.ProyectoEcommerce.ms_ordenes.clients;

import cl.ProyectoEcommerce.ms_ordenes.exceptions.CatalogoNoDisponibleException;
import cl.ProyectoEcommerce.ms_ordenes.exceptions.ProductoNoDisponibleException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

// Consulta el catálogo público de ms-productos para obtener el precio y stock REALES de cada producto.
// El precio de la orden nunca se toma del navegador (se podría manipular el request).
@Component
public class ProductoCatalogoClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductoCatalogo(Long id, String nombre, BigDecimal precio, Integer stock) {
    }

    private final RestClient restClient;

    public ProductoCatalogoClient(@Value("${services.productos.url}") String productosUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        this.restClient = RestClient.builder().baseUrl(productosUrl).requestFactory(factory).build();
    }

    public ProductoCatalogo obtener(Long productoId) {
        try {
            ProductoCatalogo producto = restClient.get()
                    .uri("/{id}", productoId)
                    .retrieve()
                    .body(ProductoCatalogo.class);
            if (producto == null || producto.precio() == null) {
                throw new ProductoNoDisponibleException(productoId);
            }
            return producto;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductoNoDisponibleException(productoId);
        } catch (ProductoNoDisponibleException e) {
            throw e;
        } catch (RestClientException e) {
            throw new CatalogoNoDisponibleException();
        }
    }
}
