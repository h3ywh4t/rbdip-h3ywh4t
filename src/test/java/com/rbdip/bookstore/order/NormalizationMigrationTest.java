package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NormalizationMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("normalization").withUsername("bookstore").withPassword("bookstore");

    private JdbcTemplate jdbc;
    private Flyway flyway;

    @BeforeEach
    void initializeLegacySchema() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load();
        flyway.clean();
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
    }

    @Test
    void preservesPopulatedOrdersAndRecoversMissingHistoricalProducts() {
        seedLegacyData();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(4);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT id FROM orders ORDER BY id", Long.class))
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(jdbc.queryForList("SELECT status FROM orders ORDER BY id", String.class))
                .containsExactly("new", "paid", "new", "new");
        assertThat(jdbc.queryForObject("SELECT created_at::text FROM orders WHERE id = 1", String.class))
                .isEqualTo("2025-01-02 03:04:05");
        List<Long> customerIds = jdbc.queryForList("SELECT customer_id FROM orders ORDER BY id", Long.class);
        assertThat(customerIds.get(0)).isEqualTo(customerIds.get(1));
        assertThat(customerIds.get(2)).isNotEqualTo(customerIds.get(0));
        assertThat(customerIds.get(3)).isNotEqualTo(customerIds.get(0));
        assertThat(jdbc.queryForList("SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(1L, 3L, 4L, 1L);
        assertThat(jdbc.queryForList("""
                SELECT p.price FROM order_items i JOIN products p ON p.id = i.product_id ORDER BY i.id
                """, BigDecimal.class)).containsExactly(
                        new BigDecimal("10.00"), new BigDecimal("12.00"),
                        new BigDecimal("7.00"), new BigDecimal("10.00"));
        assertThat(jdbc.queryForList("SELECT quantity FROM order_items ORDER BY id", Integer.class))
                .containsExactly(2, 1, 3, 4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class)).isEqualTo(1);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void enforcesRequiredForeignKeysAndDeduplicatesNullContacts() {
        seedLegacyData();
        flyway.migrate();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO orders(customer_id, status) VALUES (9999, 'new')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO orders(status) VALUES ('new')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO order_items(order_id, product_id, quantity) VALUES (1, 9999, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO order_items(order_id, quantity) VALUES (1, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customers(first_name) VALUES ('Customer')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migratesEmptyDatabaseAndAcceptsNewNormalizedRows() {
        flyway.migrate();

        jdbc.update("INSERT INTO customers(first_name, last_name, address) VALUES ('New', 'customer', 'Address')");
        jdbc.update("INSERT INTO products(name, price) VALUES ('New book', 15.00)");
        jdbc.update("INSERT INTO orders(customer_id, status) VALUES (1, 'new')");
        jdbc.update("INSERT INTO order_items(order_id, product_id, quantity) VALUES (1, 1, 2)");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'orders'
                """, String.class)).contains("customer_id")
                .doesNotContain("customer_full_name", "customer_address", "customer_phone");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'order_items'
                """, String.class)).contains("product_id").doesNotContain("product_name", "product_price");
    }

    private void seedLegacyData() {
        jdbc.update("INSERT INTO products(name, price) VALUES ('Book', 10.00), ('Book', 10.00), ('Book', 12.00)");
        jdbc.update("""
                INSERT INTO orders(customer_full_name, customer_address, customer_phone, status, created_at)
                VALUES ('Customer', NULL, NULL, 'new', '2025-01-02 03:04:05'),
                       ('Customer', NULL, NULL, 'paid', now()),
                       ('Customer', '', NULL, 'new', now()),
                       ('Customer', NULL, '', 'new', now())
                """);
        jdbc.update("""
                INSERT INTO order_items(order_id, product_name, product_price, quantity)
                VALUES (1, 'Book', 10.00, 2), (2, 'Book', 12.00, 1),
                       (3, 'Old book', 7.00, 3), (4, 'Book', 10.00, 4)
                """);
        jdbc.update("INSERT INTO reviews(product_id, author_name, rating) VALUES (1, 'Customer', 5)");
    }
}
