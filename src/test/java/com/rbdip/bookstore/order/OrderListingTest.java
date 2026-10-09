package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderListingTest extends AbstractIntegrationTest {

    private static final int MAX_ALLOWED_QUERIES = 3;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanCustomers() {
        jdbcTemplate.execute("TRUNCATE TABLE customers RESTART IDENTITY CASCADE");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 40})
    void listsDistinctOrdersAndAllItemsWithBoundedQueries(int orderCount) {
        Long firstProduct = createProduct("First Book");
        Long secondProduct = createProduct("Second Book");
        List<ListedOrder> expected = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            Long orderId = createOrder(i);
            addItem(orderId, firstProduct, 1);
            addItem(orderId, secondProduct, 3);
            addItem(orderId, firstProduct, 2);
            expected.add(new ListedOrder(orderId, "Customer " + i, "new", List.of(
                    new ListedItem("First Book", 1),
                    new ListedItem("Second Book", 3),
                    new ListedItem("First Book", 2))));
        }
        Long emptyOrder = createOrder(orderCount);
        expected.add(new ListedOrder(emptyOrder, "Customer " + orderCount, "new", List.of()));
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        ResponseEntity<List<ListedOrder>> response = listOrders();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(MAX_ALLOWED_QUERIES);
    }

    @Test
    void listsEmptyDatabase() {
        ResponseEntity<List<ListedOrder>> response = listOrders();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    private ResponseEntity<List<ListedOrder>> listOrders() {
        return restTemplate.exchange("/orders", HttpMethod.GET, null, new ParameterizedTypeReference<>() { });
    }

    private Long createProduct(String name) {
        return jdbcTemplate.queryForObject("INSERT INTO products(name, price) VALUES (?, 10) RETURNING id",
                Long.class, name);
    }

    private Long createOrder(int index) {
        Long customerId = jdbcTemplate.queryForObject("""
                INSERT INTO customers(first_name, last_name) VALUES ('Customer', ?) RETURNING id
                """, Long.class, Integer.toString(index));
        return jdbcTemplate.queryForObject("INSERT INTO orders(customer_id, status) VALUES (?, 'new') RETURNING id",
                Long.class, customerId);
    }

    private void addItem(Long orderId, Long productId, int quantity) {
        jdbcTemplate.update("INSERT INTO order_items(order_id, product_id, quantity) VALUES (?, ?, ?)",
                orderId, productId, quantity);
    }

    private record ListedOrder(long id, String customerFullName, String status, List<ListedItem> items) {
    }

    private record ListedItem(String productName, int quantity) {
    }
}
