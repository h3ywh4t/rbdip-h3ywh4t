package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRequestValidator validator;
    private final OrderItemResolver itemResolver;
    private final PricingCalculator pricingCalculator;
    private final OrderWriter orderWriter;
    private final OrderConfirmationNotifier notifier;

    public OrderService(
            OrderRequestValidator validator,
            OrderItemResolver itemResolver,
            PricingCalculator pricingCalculator,
            OrderWriter orderWriter,
            OrderConfirmationNotifier notifier) {
        this.validator = validator;
        this.itemResolver = itemResolver;
        this.pricingCalculator = pricingCalculator;
        this.orderWriter = orderWriter;
        this.notifier = notifier;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);
        List<ResolvedOrderItem> items = itemResolver.resolve(request.items());
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                items.stream().map(ResolvedOrderItem::toLineItem).toList(),
                request.customerType(), request.couponCode());
        Order order = orderWriter.save(request, items);
        notifier.send(request.customerFullName(), order.getId(), total);
        return order;
    }
}
