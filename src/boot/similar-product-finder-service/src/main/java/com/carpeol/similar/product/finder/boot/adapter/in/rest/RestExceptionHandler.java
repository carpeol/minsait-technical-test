package com.carpeol.similar.product.finder.boot.adapter.in.rest;

import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ProductNotFound.class)
    ResponseEntity<Void> handleProductNotFound(ProductNotFound exception) {
        LOGGER.warn("Returning 404 for missing product: {}", exception.getMessage());
        return ResponseEntity.notFound().build();
    }
}
