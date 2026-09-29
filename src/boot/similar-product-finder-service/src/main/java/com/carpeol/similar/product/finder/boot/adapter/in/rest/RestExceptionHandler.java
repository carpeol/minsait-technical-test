package com.carpeol.similar.product.finder.boot.adapter.in.rest;

import com.carpeol.similar.product.finder.domain.exception.ProductNotFound;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(ProductNotFound.class)
    ResponseEntity<Void> handleProductNotFound(ProductNotFound exception) {
        return ResponseEntity.notFound().build();
    }
}
