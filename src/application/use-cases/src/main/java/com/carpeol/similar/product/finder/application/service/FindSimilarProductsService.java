package com.carpeol.similar.product.finder.application.service;

import com.carpeol.similar.product.finder.application.port.in.FindSimilarProductsUseCase;
import com.carpeol.similar.product.finder.application.query.FindSimilarProductsQuery;
import com.carpeol.similar.product.finder.application.result.SimilarProduct;
import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import com.carpeol.similar.product.finder.domain.model.Product;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;

import java.util.List;
import java.util.Objects;

public class FindSimilarProductsService implements FindSimilarProductsUseCase {

    private final ProductRepository productRepository;

    public FindSimilarProductsService(ProductRepository productRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository must not be null");
    }

    @Override
    public List<SimilarProduct> findSimilarProducts(FindSimilarProductsQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        if (!productRepository.existsById(query.productId()))
            throw new ProductNotFound(query.productId());

        return productRepository.findSimilarProductIds(query.productId()).stream()
                .map(productRepository::getById)
                .map(this::toSimilarProduct)
                .toList();
    }

    private SimilarProduct toSimilarProduct(Product product) {
        return new SimilarProduct(
                product.productId().value(),
                product.productName().value(),
                product.productPrice().value(),
                product.productAvailability().value());
    }
}
