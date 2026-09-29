# Similar Product Finder

## Overview

The service implements `GET /product/{productId}/similar` on port `5000`. It obtains the ordered similar-product IDs from the existing product API and resolves every product detail before returning the response defined in [similarProducts.yaml](./similarProducts.yaml).

The implementation follows hexagonal architecture:

- `src/domain/model`: domain model, value objects, exceptions, and repository port.
- `src/application/use-cases`: use case and its input/output ports.
- `src/infrastructure/product-repository-rest`: generated HTTP client contract and REST repository adapter.
- `src/boot/similar-product-finder-service`: Spring Boot composition, generated inbound API, REST controller, Actuator, Swagger UI, and container packaging.

OpenAPI contracts are copied into each module's `src/main/resources/openapi` directory and Maven generates the corresponding interfaces and DTOs during `generate-sources`.

## Prerequisites

- JDK 25
- Maven
- Docker and Docker Compose

## Local execution

Start the provided mock API and observability stack:

```bash
docker compose up -d simulado influxdb grafana
```

Run the application locally:

```bash
mvn -pl src/boot/similar-product-finder-service -am spring-boot:run
```

The application listens on `http://localhost:5000`. Its upstream endpoint defaults to `http://localhost:3001`; override it with `SIMILAR_PRODUCTS_API_BASE_URL` when needed.

Useful endpoints:

- API: `http://localhost:5000/product/1/similar`
- Swagger UI: `http://localhost:5000/swagger-ui.html`
- OpenAPI document: `http://localhost:5000/v3/api-docs`
- Health: `http://localhost:5000/actuator/health`
- Prometheus metrics: `http://localhost:5000/actuator/prometheus`

## Container execution

Build and start every service, including the application:

```bash
docker compose up --build -d
```

Inside Compose, the service uses `http://simulado` as its upstream product API and publishes port `5000` on the host.

Stop the stack with:

```bash
docker compose down
```

## Tests

Run all unit, adapter, contract-generation, Spring context, and MockMvc tests from the repository root:

```bash
mvn clean install
```

## Similar product lookup behavior

The requested product is checked first; if it does not exist, the endpoint returns `404` and does not request similar IDs. Missing details for a similar-product ID are different: the REST repository adapter logs a warning, omits that product from the result, and continues returning the other available products with `200`.

The application service requests all similar product details through the `ProductRepository` port. The REST adapter resolves those details concurrently using virtual threads, then returns the found products in the original similarity order. This keeps transport-level concurrency in the infrastructure adapter and leaves the use case responsible for application orchestration and response mapping.

## Performance self-evaluation

With the application available on port `5000`, execute the provided test:

```bash
docker compose run --rm k6 run scripts/test.js
```

Inspect the K6 dashboard at `http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`.
