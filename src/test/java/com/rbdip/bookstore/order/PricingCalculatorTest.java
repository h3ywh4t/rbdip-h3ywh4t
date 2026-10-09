package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest(name = "price={0}, quantity={1}, customer={2}, coupon={3} -> {4}")
    @CsvSource(value = {
        "10.00, 2, regular, NULL, 20.00",
        "100.00, 1, vip, NULL, 90.00",
        "100.00, 1, wholesale, NULL, 85.00",
        "100.00, 1, NULL, NULL, 100.00",
        "100.00, 1, unknown, UNKNOWN, 100.00",
        "100.00, 1, VIP, save10, 100.00",
        "100.00, 1, '', '', 100.00",
        "100.00, 1, regular, SAVE10, 90.00",
        "100.00, 1, regular, SAVE20PERCENT, 80.00",
        "100.00, 1, vip, SAVE10, 80.00",
        "100.00, 1, wholesale, SAVE20PERCENT, 68.00",
        "10.00, 10, regular, NULL, 100.00",
        "10.00, 11, regular, NULL, 104.50",
        "10.00, 11, vip, SAVE20PERCENT, 75.24",
        "10.00, 11, wholesale, SAVE10, 78.83",
        "0.00, 1, regular, NULL, 0.00",
        "9.99, 1, regular, SAVE10, 0.00",
        "10.00, 1, regular, SAVE10, 0.00",
        "10.01, 1, regular, SAVE10, 0.01",
        "-10.00, 1, vip, SAVE20PERCENT, 0.00",
        "10.00, 0, regular, NULL, 0.00",
        "10.00, -1, regular, NULL, 0.00",
        "999.99, 1, regular, NULL, 999.99",
        "1000.00, 1, regular, NULL, 1000.00",
        "1000.01, 1, regular, NULL, 980.01",
        "2000.00, 1, regular, NULL, 1960.00",
        "100.00, 11, regular, NULL, 1024.10",
        "1111.11, 1, vip, NULL, 1000.00",
        "1111.12, 1, vip, NULL, 980.01",
        "1010.00, 1, regular, SAVE10, 1000.00",
        "1010.01, 1, regular, SAVE10, 980.01",
        "1250.00, 1, regular, SAVE20PERCENT, 1000.00",
        "1250.01, 1, regular, SAVE20PERCENT, 980.01",
        "1.004, 1, regular, NULL, 1.00",
        "1.005, 1, regular, NULL, 1.01"
    }, nullValues = "NULL")
    void characterizesDiscountsBoundariesAndRounding(
            String price, int quantity, String customerType, String couponCode, String expected) {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(item(price, quantity)), customerType, couponCode);

        assertThat(total).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void emptyOrderHasZeroTotalEvenWithACoupon() {
        assertThat(calculator.calculateOrderTotal(List.of(), "vip", "SAVE10"))
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void appliesBulkDiscountPerLineRatherThanToCombinedQuantity() {
        assertThat(calculator.calculateOrderTotal(
                List.of(item("10.00", 6), item("10.00", 6)), "regular", null))
                .isEqualTo(new BigDecimal("120.00"));
        assertThat(calculator.calculateOrderTotal(
                List.of(item("10.00", 11), item("20.00", 2)), "regular", null))
                .isEqualTo(new BigDecimal("144.50"));
    }

    @Test
    void roundsOnlyAfterAddingLines() {
        assertThat(calculator.calculateOrderTotal(
                List.of(item("0.004", 1), item("0.004", 1)), "regular", null))
                .isEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void clampsNegativeTotalAfterAddingAllLines() {
        assertThat(calculator.calculateOrderTotal(
                List.of(item("-10.00", 1), item("20.00", 1)), "regular", null))
                .isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void nullDataRetainsExistingExceptions() {
        assertThatThrownBy(() -> calculator.calculateOrderTotal(null, "regular", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculateOrderTotal(Arrays.asList((PricingCalculator.LineItem) null),
                "regular", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(null, 1)), "regular", null))
                .isInstanceOf(NullPointerException.class);
    }

    private PricingCalculator.LineItem item(String price, int quantity) {
        return new PricingCalculator.LineItem(new BigDecimal(price), quantity);
    }
}
