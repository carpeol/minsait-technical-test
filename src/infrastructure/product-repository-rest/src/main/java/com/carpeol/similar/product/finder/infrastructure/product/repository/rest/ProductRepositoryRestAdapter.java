package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import com.carpeol.similar.product.finder.domain.exception.ProductRepositoryError;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import com.carpeol.similar.product.finder.domain.valueobject.ProductAvailability;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.domain.valueobject.ProductName;
import com.carpeol.similar.product.finder.domain.valueobject.ProductPrice;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.model.ProductDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ProductRepositoryRestAdapter implements ProductRepository {

    private final DefaultApi productApi;

    public ProductRepositoryRestAdapter(DefaultApi productApi) {
        this.productApi = Objects.requireNonNull(productApi, "productApi must not be null");
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
        try {
            ResponseEntity<ProductDetail> response = requireResponse(productApi.getProductProductId(productId.value().toString()));
            if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
                return Optional.empty();
            }

            return Optional.of(toDomainProduct(requireSuccessfulResponse(response, "retrieving product details", productId)));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();

        } catch (RestClientException exception) {
            throw new ProductRepositoryError("Error retrieving product details for ID: " + productId.value(), exception);
        }
    }

    @Override
    public List<ProductId> findSimilarProductIds(ProductId productId) {
        try {
            ResponseEntity<java.util.Set<String>> response = requireResponse(
                    productApi.getProductSimilarids(productId.value().toString()));

            return requireSuccessfulResponse(response, "retrieving similar product IDs", productId).stream()
                    .map(value -> new ProductId(Long.valueOf(value)))
                    .toList();
        } catch (RestClientException exception) {
            throw new ProductRepositoryError("Error retrieving similar product IDs for ID: " + productId.value(), exception);
        }
    }

    @Override
    public boolean existsById(ProductId productId) {
        try {
            ResponseEntity<ProductDetail> response = requireResponse(productApi.getProductProductId(productId.value().toString()));
            if (HttpStatus.NOT_FOUND.equals(response.getStatusCode())) {
                return false;
            }

            requireSuccessfulStatus(response, "checking existence", productId);
            return true;
        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        } catch (RestClientException exception) {
            throw new ProductRepositoryError("Error checking existence for product ID: " + productId.value(), exception);
        }
    }

    private Product toDomainProduct(ProductDetail productDetail) {
        return new Product(
                new ProductId(Long.valueOf(productDetail.getId())),
                new ProductName(productDetail.getName()),
                new ProductPrice(productDetail.getPrice()),
                new ProductAvailability(productDetail.getAvailability()));
    }

    private <T> T requireResponse(T response) {
        return Objects.requireNonNull(response, "Product API returned an empty response body");
    }

    private <T> T requireSuccessfulResponse(ResponseEntity<T> response, String operation, ProductId productId) {
        requireSuccessfulStatus(response, operation, productId);
        return requireResponse(response.getBody());
    }

    private void requireSuccessfulStatus(ResponseEntity<?> response, String operation, ProductId productId) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ProductRepositoryError(
                    "Product API returned status " + response.getStatusCode() + " while " + operation + " for ID: "
                            + productId.value());
        }
    }
}
