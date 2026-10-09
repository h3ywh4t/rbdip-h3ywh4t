package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerService;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderCreationTest {

    @Test
    void resolvesRepeatedProductsWithIndependentQuantitiesAndDefault() {
        ProductRepository products = mock(ProductRepository.class);
        Product product = new Product("Book", BigDecimal.TEN, null);
        when(products.findById(1L)).thenReturn(Optional.of(product));

        List<ResolvedOrderItem> items = new OrderItemResolver(products).resolve(List.of(
                new CreateOrderRequest.Item(1L, null), new CreateOrderRequest.Item(1L, 2)));

        assertThat(items).extracting(ResolvedOrderItem::quantity).containsExactly(1, 2);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.product()).isSameAs(product);
            OrderItem savedItem = item.toOrderItem(2L);
            assertThat(savedItem.getOrderId()).isEqualTo(2L);
            assertThat(savedItem.getProductName()).isEqualTo("Book");
            assertThat(savedItem.getProductPrice()).isEqualTo(BigDecimal.TEN);
            assertThat(savedItem.getQuantity()).isEqualTo(item.quantity());
        });
    }

    @Test
    void checksProductExistenceBeforeQuantity() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById(1L)).thenReturn(Optional.empty());
        OrderItemResolver resolver = new OrderItemResolver(products);

        assertThatThrownBy(() -> resolver.resolve(List.of(new CreateOrderRequest.Item(1L, 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("product 1 not found");
        when(products.findById(1L)).thenReturn(Optional.of(new Product("Book", BigDecimal.TEN, null)));
        assertThatThrownBy(() -> resolver.resolve(List.of(new CreateOrderRequest.Item(1L, 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("quantity must be positive");
    }

    @Test
    void savesBeforeSendingConfirmationAndPreservesTotal() {
        ProductRepository products = mock(ProductRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        OrderConfirmationNotifier notifier = mock(OrderConfirmationNotifier.class);
        CustomerService customers = mock(CustomerService.class);
        when(customers.findOrCreate("Customer", "Address", null))
                .thenReturn(new Customer("Customer", "Address", null));
        when(products.findById(1L)).thenReturn(Optional.of(new Product("Book", BigDecimal.TEN, null)));
        Order saved = mock(Order.class);
        when(saved.getId()).thenReturn(2L);
        when(orders.save(any(Order.class))).thenReturn(saved);
        OrderService service = new OrderService(new OrderRequestValidator(), new OrderItemResolver(products),
                new PricingCalculator(), new OrderWriter(customers, orders, orderItems), notifier);
        CreateOrderRequest request = new CreateOrderRequest(
                "Customer", "Address", null, null, null, List.of(new CreateOrderRequest.Item(1L, 2)));

        assertThat(service.createOrder(request)).isSameAs(saved);

        var calls = inOrder(orders, orderItems, notifier);
        calls.verify(orders).save(any(Order.class));
        calls.verify(orderItems).save(any(OrderItem.class));
        calls.verify(notifier).send("Customer", 2L, new BigDecimal("20.00"));
        assertThatThrownBy(() -> service.createOrder(new CreateOrderRequest(
                " ", "Address", null, null, null, List.of())))
                .hasMessage("customerFullName is required");
        verify(products, times(1)).findById(1L);
        verify(orders, times(1)).save(any(Order.class));
        verify(notifier, times(1)).send("Customer", 2L, new BigDecimal("20.00"));
    }
}
