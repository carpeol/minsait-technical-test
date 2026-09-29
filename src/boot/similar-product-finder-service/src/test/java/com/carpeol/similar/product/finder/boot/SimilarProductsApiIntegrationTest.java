package com.carpeol.similar.product.finder.boot;

import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.api.DefaultApi;
import com.carpeol.similar.product.finder.infrastructure.product.repository.rest.generated.model.ProductDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SimilarProductsApiIntegrationTest.ExternalProductApiMockConfiguration.class)
class SimilarProductsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DefaultApi externalProductApi;

    @Test
    void returnsSimilarProductDetailsInSimilarityOrder() throws Exception {
        when(externalProductApi.getProductProductId("1")).thenReturn(ResponseEntity.ok(product("1", "Shirt", "9.99", true)));
        when(externalProductApi.getProductSimilarids("1"))
                .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2", "3"))));
        when(externalProductApi.getProductProductId("2")).thenReturn(ResponseEntity.ok(product("2", "Dress", "19.99", true)));
        when(externalProductApi.getProductProductId("3")).thenReturn(ResponseEntity.ok(product("3", "Blazer", "29.99", false)));

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].id").value("2"))
                .andExpect(jsonPath("$[0].name").value("Dress"))
                .andExpect(jsonPath("$[0].price").value(19.99))
                .andExpect(jsonPath("$[0].availability").value(true))
                .andExpect(jsonPath("$[1].id").value("3"))
                .andExpect(jsonPath("$[1].name").value("Blazer"))
                .andExpect(jsonPath("$[1].price").value(29.99))
                .andExpect(jsonPath("$[1].availability").value(false));

        verify(externalProductApi).getProductProductId("1");
        verify(externalProductApi).getProductSimilarids("1");
        verify(externalProductApi).getProductProductId("2");
        verify(externalProductApi).getProductProductId("3");
    }

    @Test
    void returnsNotFoundWhenRequestedProductDoesNotExist() throws Exception {
        when(externalProductApi.getProductProductId("4")).thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        mockMvc.perform(get("/product/4/similar"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(externalProductApi).getProductProductId("4");
        verify(externalProductApi, never()).getProductSimilarids("4");
    }

    @Test
    void omitsSimilarProductsWhoseDetailsAreNotFound() throws Exception {
        when(externalProductApi.getProductProductId("1")).thenReturn(ResponseEntity.ok(product("1", "Shirt", "9.99", true)));
        when(externalProductApi.getProductSimilarids("1"))
                .thenReturn(ResponseEntity.ok(new LinkedHashSet<>(List.of("2", "3"))));
        when(externalProductApi.getProductProductId("2")).thenReturn(ResponseEntity.ok(product("2", "Dress", "19.99", true)));
        when(externalProductApi.getProductProductId("3")).thenReturn(ResponseEntity.notFound().build());

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("2"))
                .andExpect(jsonPath("$[0].name").value("Dress"));
    }

    @Test
    void exposesPrometheusMetricsThroughActuator() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    void exposesTheGeneratedOpenApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.operationId")
                        .value("getProductSimilar"))
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.200").exists())
                .andExpect(jsonPath("$.paths['/product/{productId}/similar'].get.responses.404").exists())
                .andExpect(jsonPath("$.components.schemas.ProductDetail.required").isArray());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    private ProductDetail product(String id, String name, String price, boolean availability) {
        return new ProductDetail(id, name, new BigDecimal(price), availability);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalProductApiMockConfiguration {

        @Bean
        @Primary
        DefaultApi externalProductApi() {
            return mock(DefaultApi.class);
        }
    }
}
