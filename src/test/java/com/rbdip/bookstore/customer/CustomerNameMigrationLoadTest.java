package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbdip.bookstore.BookstoreApplication;
import com.rbdip.bookstore.order.CreateOrderRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CustomerNameMigrationLoadTest {

    private static final int WORKERS = 2;
    private static final int ORDERS_PER_WORKER = 20;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("name_migration_load").withUsername("bookstore").withPassword("bookstore");

    @Test
    void contractKeepsOrderApiWorkingUnderConcurrentReadsAndWrites() throws Exception {
        PGSimpleDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        prepareSchema(dataSource, jdbc);
        CountDownLatch migrationStarted = new CountDownLatch(1);
        CountDownLatch initialRequestsCompleted = new CountDownLatch(WORKERS);
        CountDownLatch contractApplied = new CountDownLatch(1);
        Callback callback = loadCallback(jdbc, migrationStarted, initialRequestsCompleted, contractApplied);

        try (ConfigurableApplicationContext app = startApplication();
                var executor = Executors.newFixedThreadPool(WORKERS + 1);
                HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build()) {
            String baseUrl = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
            ObjectMapper mapper = app.getBean(ObjectMapper.class);
            Long productId = createProduct(client, baseUrl, mapper);
            createAndReadOrder(client, baseUrl, mapper, productId, "Before Contract");
            assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE first_name = 'Before'",
                    String.class)).isEqualTo("Before Contract");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM orders WHERE customer_full_name = 'Before Contract'
                    """, Integer.class)).isEqualTo(1);
            List<Future<?>> requests = new ArrayList<>();
            for (int i = 0; i < WORKERS; i++) {
                String name = "Load Customer " + i;
                requests.add(executor.submit(() -> {
                    await(migrationStarted);
                    createAndReadOrder(client, baseUrl, mapper, productId, name);
                    initialRequestsCompleted.countDown();
                    await(contractApplied);
                    for (int j = 1; j < ORDERS_PER_WORKER; j++) {
                        createAndReadOrder(client, baseUrl, mapper, productId, name);
                    }
                    return null;
                }));
            }
            Future<?> migration = executor.submit(() -> {
                try {
                    assertThat(Flyway.configure().dataSource(dataSource).callbacks(callback).load()
                            .migrate().migrationsExecuted).isEqualTo(1);
                } finally {
                    migrationStarted.countDown();
                    contractApplied.countDown();
                }
            });
            for (Future<?> request : requests) {
                request.get(30, TimeUnit.SECONDS);
            }
            migration.get(30, TimeUnit.SECONDS);
            createAndReadOrder(client, baseUrl, mapper, productId, "After Contract");
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isEqualTo(WORKERS * ORDERS_PER_WORKER + 3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE (table_name = 'customers' AND column_name = 'full_name')
                   OR (table_name = 'orders' AND column_name = 'customer_full_name')
                """, Integer.class)).isZero();
    }

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private void prepareSchema(PGSimpleDataSource dataSource, JdbcTemplate jdbc) {
        Flyway.configure().dataSource(dataSource).target("2").load().migrate();
        jdbc.update("INSERT INTO customers(full_name, address) VALUES ('Old Customer', 'Old address')");
        jdbc.update("INSERT INTO orders(customer_id, status) VALUES (1, 'new')");
        Flyway.configure().dataSource(dataSource).target("4").load().migrate();
    }

    private ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(BookstoreApplication.class).run(
                "--server.port=0", "--spring.flyway.enabled=false",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.hikari.connection-init-sql=SET application_name = 'name-migration-api'",
                "--logging.level.root=ERROR");
    }

    private Long createProduct(HttpClient client, String baseUrl, ObjectMapper mapper) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/products"))
                .timeout(REQUEST_TIMEOUT).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Book\",\"price\":\"10.00\"}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body()).get("id").asLong();
    }

    private void createAndReadOrder(HttpClient client, String baseUrl, ObjectMapper mapper,
            Long productId, String name) throws Exception {
        CreateOrderRequest body = new CreateOrderRequest(name, "Address", null, "regular", null,
                List.of(new CreateOrderRequest.Item(productId, 1)));
        HttpRequest post = HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
                .timeout(REQUEST_TIMEOUT).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> created = client.send(post, HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        long orderId = mapper.readTree(created.body()).get("id").asLong();
        HttpRequest get = HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
                .timeout(REQUEST_TIMEOUT).GET().build();
        HttpResponse<String> listed = client.send(get, HttpResponse.BodyHandlers.ofString());
        assertThat(listed.statusCode()).as(listed.body()).isEqualTo(200);
        boolean found = false;
        for (JsonNode order : mapper.readTree(listed.body())) {
            if (order.get("id").asLong() == orderId) {
                assertThat(order.get("customerFullName").asText()).isEqualTo(name);
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    private Callback loadCallback(JdbcTemplate jdbc, CountDownLatch migrationStarted,
            CountDownLatch initialRequestsCompleted, CountDownLatch contractApplied) {
        return new Callback() {
            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.BEFORE_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                if (event == Event.BEFORE_EACH_MIGRATE) {
                    migrationStarted.countDown();
                    await(initialRequestsCompleted);
                } else {
                    contractApplied.countDown();
                    awaitBlockedApiRequest(jdbc);
                }
            }

            @Override
            public String getCallbackName() {
                return "concurrent-api-requests";
            }
        };
    }

    private void awaitBlockedApiRequest(JdbcTemplate jdbc) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        int blocked = 0;
        while (blocked == 0 && System.nanoTime() < deadline) {
            blocked = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE application_name = 'name-migration-api' AND wait_event_type = 'Lock'
                    """, Integer.class);
            if (blocked == 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }
        }
        assertThat(blocked).as("API request waiting for the contract transaction").isPositive();
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
