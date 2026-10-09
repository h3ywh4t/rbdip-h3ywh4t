package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class NormalizedOrderTest extends AbstractIntegrationTest {

    @BeforeEach
    void cleanCustomers() {
        jdbcTemplate.execute("TRUNCATE TABLE customers RESTART IDENTITY CASCADE");
    }

    @Test
    void repeatedOrdersReuseCustomerAndReferenceOriginalProduct() {
        Long productId = createProduct();
        CreateOrderRequest request = request(productId, "Address", null);
        createOrder(request);
        createOrder(request);

        assertThat(count("customers")).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT product_id FROM order_items", Long.class))
                .containsExactly(productId, productId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT customer_id) FROM orders", Integer.class))
                .isEqualTo(1);
        ResponseEntity<List> response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        Map<?, ?> order = (Map<?, ?>) response.getBody().get(0);
        assertThat(order.get("customerFullName")).isEqualTo("Customer");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM orders o
                JOIN customers c ON c.id = o.customer_id
                JOIN order_items i ON i.order_id = o.id
                JOIN products p ON p.id = i.product_id
                WHERE c.full_name = 'Customer' AND p.name = 'Book' AND i.quantity = 1
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void differentContactsAreNotMergedAndNullPhoneDiffersFromEmptyPhone() {
        Long productId = createProduct();
        createOrder(request(productId, "Address", null));
        createOrder(request(productId, "Other address", null));
        createOrder(request(productId, "Address", ""));
        createOrder(request(productId, "Address", ""));

        assertThat(count("customers")).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM customers WHERE full_name = 'Customer' AND phone IS NULL
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void rejectedOrderDoesNotPersistCustomerOrderOrItems() {
        Long productId = createProduct();
        CreateOrderRequest request = new CreateOrderRequest("Customer", "Address", null, "regular", null,
                List.of(new CreateOrderRequest.Item(productId, 1), new CreateOrderRequest.Item(productId, 0)));

        ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "quantity must be positive");
        assertThat(count("customers")).isZero();
        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private Long createProduct() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/products", Map.of("name", "Book", "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(response.getBody().get("id").toString());
    }

    private CreateOrderRequest request(Long productId, String address, String phone) {
        return new CreateOrderRequest("Customer", address, phone, "regular", null,
                List.of(new CreateOrderRequest.Item(productId, null)));
    }

    private void createOrder(CreateOrderRequest request) {
        assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }
}
