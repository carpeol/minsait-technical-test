package com.carpeol.similar.product.finder.infrastructure.product.repository.rest;

import com.carpeol.similar.product.finder.domain.exception.ProductRepositoryError;
import com.carpeol.similar.product.finder.domain.repository.ProductRepository;
import com.carpeol.similar.product.finder.domain.valueobject.ProductId;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProductRepositoryRestCacheTest {

    @Test
    void cachesSimilarProductIdsAndExpiresThemAfterConfiguredTtl() throws InterruptedException {
        try (AnnotationConfigApplicationContext context = createContext(true, "100ms")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductSimilarids("1"))
                    .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2", "3"))));

            List<ProductId> expected = List.of(new ProductId(2L), new ProductId(3L));
            assertEquals(expected, repository.findSimilarProductIds(new ProductId(1L)));
            assertEquals(expected, repository.findSimilarProductIds(new ProductId(1L)));
            verify(productApi, times(1)).getProductSimilarids("1");

            TimeUnit.MILLISECONDS.sleep(250);

            assertEquals(expected, repository.findSimilarProductIds(new ProductId(1L)));
            verify(productApi, times(2)).getProductSimilarids("1");
            assertInstanceOf(CaffeineCacheManager.class, context.getBean(CacheManager.class));
        }
    }

    @Test
    void bypassesCacheWhenDisabled() {
        try (AnnotationConfigApplicationContext context = createContext(false, "5m")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductSimilarids("1"))
                    .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2"))));

            repository.findSimilarProductIds(new ProductId(1L));
            repository.findSimilarProductIds(new ProductId(1L));

            verify(productApi, times(2)).getProductSimilarids("1");
            assertInstanceOf(NoOpCacheManager.class, context.getBean(CacheManager.class));
        }
    }

    @Test
    void doesNotCacheFailedRequests() {
        try (AnnotationConfigApplicationContext context = createContext(true, "5m")) {
            ProductRepository repository = context.getBean(ProductRepository.class);
            DefaultApi productApi = context.getBean("testProductApi", DefaultApi.class);
            when(productApi.getProductSimilarids("1"))
                    .thenThrow(new ResourceAccessException("Connection refused"))
                    .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2"))));

            assertThrows(ProductRepositoryError.class,
                    () -> repository.findSimilarProductIds(new ProductId(1L)));
            assertEquals(List.of(new ProductId(2L)),
                    repository.findSimilarProductIds(new ProductId(1L)));
            verify(productApi, times(2)).getProductSimilarids("1");
        }
    }

    private AnnotationConfigApplicationContext createContext(boolean cacheEnabled, String cacheTtl) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test-properties",
                Map.of(
                        "similar-products.api.base-url", "http://localhost:3001",
                        "similar-products.cache.enabled", cacheEnabled,
                        "similar-products.cache.ttl", cacheTtl)));
        context.register(TestConfiguration.class, ProductRepositoryRestConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {

        @Bean
        @Primary
        DefaultApi testProductApi() {
            return mock(DefaultApi.class);
        }
    }
}
