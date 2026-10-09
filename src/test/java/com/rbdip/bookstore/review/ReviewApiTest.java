package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReviewApiTest extends AbstractIntegrationTest {

    @Autowired
    private PurchaseHistory purchaseHistory;

    @BeforeEach
    void cleanCustomers() {
        jdbcTemplate.execute("TRUNCATE TABLE customers RESTART IDENTITY CASCADE");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsNamedReviewsWithAndWithoutPurchases(boolean purchased) {
        Long productId = createProduct();
        if (purchased) {
            CreateOrderRequest request = new CreateOrderRequest("Buyer", "Address", null, "regular", null,
                    List.of(new CreateOrderRequest.Item(productId, 1)));
            assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }
        assertThat(purchaseHistory.hasPurchases()).isEqualTo(purchased);

        addReview(productId, Map.of("authorName", "Buyer", "rating", 4, "comment", "Helpful"));

        assertThat(listReviews(productId))
                .containsExactly(Map.of("authorName", "Buyer", "rating", 4, "comment", "Helpful"));
    }

    @Test
    void defaultsAnonymousAuthorAndEmptyCommentAndKeepsReviewsPerProduct() {
        Long productId = createProduct();
        Long otherProduct = createProduct();

        addReview(productId, Map.of("rating", 5));

        assertThat(listReviews(productId))
                .containsExactly(Map.of("authorName", "anonymous", "rating", 5, "comment", ""));
        assertThat(listReviews(otherProduct)).isEmpty();
    }

    @Test
    void orderWithoutItemsDoesNotCountAsPurchaseOrPreventReview() {
        Long productId = createProduct();
        Long customerId = jdbcTemplate.queryForObject("INSERT INTO customers(first_name) VALUES ('Buyer') RETURNING id",
                Long.class);
        jdbcTemplate.update("INSERT INTO orders(customer_id, status) VALUES (?, 'new')", customerId);

        assertThat(purchaseHistory.hasPurchases()).isFalse();
        addReview(productId, Map.of("rating", 5));

        assertThat(listReviews(productId)).hasSize(1);
    }

    private Long createProduct() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/products",
                Map.of("name", "Book", "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(response.getBody().get("id").toString());
    }

    private void addReview(Long productId, Map<String, Object> request) {
        ResponseEntity<Map> response = restTemplate.postForEntity("/products/" + productId + "/reviews",
                request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("id")).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listReviews(Long productId) {
        ResponseEntity<List> response = restTemplate.getForEntity("/products/" + productId + "/reviews", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
