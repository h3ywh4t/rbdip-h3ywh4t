package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OrderItemResolver {

    private static final int DEFAULT_QUANTITY = 1;
    private final ProductRepository productRepository;

    public OrderItemResolver(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ResolvedOrderItem> resolve(List<CreateOrderRequest.Item> items) {
        List<ResolvedOrderItem> resolved = new ArrayList<>();
        for (CreateOrderRequest.Item item : items) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("product " + item.productId() + " not found"));
            int quantity = item.quantity() == null ? DEFAULT_QUANTITY : item.quantity();
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            resolved.add(new ResolvedOrderItem(product, quantity));
        }
        return resolved;
    }
}
