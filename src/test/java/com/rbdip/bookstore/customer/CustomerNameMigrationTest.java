package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class CustomerNameMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("customer_names").withUsername("bookstore").withPassword("bookstore");

    private PGSimpleDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void initializeNormalizedSchema() {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        migrateTo("2");
    }

    @Test
    void supportsOldAndNewWritesUntilContract() {
        jdbc.update("INSERT INTO customers(full_name) VALUES ('Ivan Petrov')");
        jdbc.update("INSERT INTO orders(customer_id, status) VALUES (1, 'new')");

        migrateTo("3");

        jdbc.update("UPDATE customers SET full_name = 'Ivan Ivanov' WHERE id = 1");
        assertThat(jdbc.queryForObject("SELECT first_name FROM customers WHERE id = 1", String.class))
                .isEqualTo("Ivan");
        assertThat(jdbc.queryForObject("SELECT last_name FROM customers WHERE id = 1", String.class))
                .isEqualTo("Ivanov");
        assertThat(jdbc.queryForObject("SELECT customer_full_name FROM orders WHERE id = 1", String.class))
                .isEqualTo("Ivan Ivanov");
        jdbc.update("INSERT INTO customers(first_name, last_name) VALUES ('Anna', 'Sidorova')");
        jdbc.update("INSERT INTO orders(customer_id, status) VALUES (2, 'new')");
        jdbc.update("UPDATE customers SET first_name = 'Maria' WHERE id = 2");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE id = 2", String.class))
                .isEqualTo("Maria Sidorova");
        assertThat(jdbc.queryForObject("SELECT customer_full_name FROM orders WHERE id = 2", String.class))
                .isEqualTo("Maria Sidorova");

        migrateTo("4");

        jdbc.update("INSERT INTO customers(full_name, phone) VALUES ('Legacy Customer', 'old')");
        jdbc.update("INSERT INTO customers(first_name, last_name, phone) VALUES ('Modern', 'Customer', 'new')");
        assertThat(jdbc.queryForObject("SELECT first_name FROM customers WHERE phone = 'old'", String.class))
                .isEqualTo("Legacy");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE phone = 'new'", String.class))
                .isEqualTo("Modern Customer");

        migrateTo("5");

        assertThat(columns("customers")).contains("first_name", "last_name").doesNotContain("full_name");
        assertThat(columns("orders")).contains("customer_id").doesNotContain("customer_full_name");
        jdbc.update("INSERT INTO customers(first_name) VALUES ('Single')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isEqualTo(5);
    }

    @Test
    void expandsPreviouslyNormalizedSchemaWithoutTheLegacyOrderColumn() {
        jdbc.update("INSERT INTO customers(full_name) VALUES ('Existing Customer')");
        jdbc.update("INSERT INTO orders(customer_id, status) VALUES (1, 'new')");
        jdbc.execute("DROP TRIGGER customers_order_names_sync ON customers");
        jdbc.execute("DROP TRIGGER orders_customer_name_sync ON orders");
        jdbc.execute("DROP FUNCTION sync_customer_order_names()");
        jdbc.execute("DROP FUNCTION sync_order_customer_name()");
        jdbc.execute("ALTER TABLE orders DROP COLUMN customer_full_name");

        migrateTo("3");

        assertThat(jdbc.queryForObject("SELECT customer_full_name FROM orders WHERE id = 1", String.class))
                .isEqualTo("Existing Customer");
        migrateTo("4");
        migrateTo("5");
        assertThat(jdbc.queryForObject("SELECT first_name FROM customers WHERE id = 1", String.class))
                .isEqualTo("Existing");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void backfillPreservesExactNamesAndKeepsSingleNamesDistinctFromTrailingSpaces() {
        List<String> names = List.of("Ivan Petrov", "Madonna", "Ivan  Petrov", "Ivan ", " Ivan", "Иван Петров");
        for (String name : names) {
            jdbc.update("INSERT INTO customers(full_name) VALUES (?)", name);
        }

        migrateTo("3");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE first_name IS NULL", Integer.class))
                .isEqualTo(names.size());
        migrateTo("4");

        assertThat(jdbc.queryForList("""
                SELECT first_name || CASE WHEN last_name IS NULL THEN '' ELSE ' ' || last_name END
                FROM customers ORDER BY id
                """, String.class)).containsExactlyElementsOf(names);
        assertThat(jdbc.queryForObject("SELECT last_name FROM customers WHERE first_name = 'Madonna'", String.class))
                .isNull();
        migrateTo("5");
        jdbc.update("INSERT INTO customers(first_name) VALUES ('Ivan')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customers(first_name, last_name) VALUES ('Ivan', '')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customers(first_name, last_name) VALUES (?, ?)",
                "A".repeat(255), "B"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = ?
                """, String.class, table);
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(dataSource).target(version).load().migrate();
    }
}
