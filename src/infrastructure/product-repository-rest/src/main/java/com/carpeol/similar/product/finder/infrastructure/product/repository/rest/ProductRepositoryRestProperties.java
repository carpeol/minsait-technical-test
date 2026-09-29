package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties("similar-products.api")
public record ProductRepositoryRestProperties(URI baseUrl) {
}
