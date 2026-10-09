package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CustomerNameTest {

    @ParameterizedTest
    @MethodSource("names")
    void preservesFullNameWhileSeparatingItsParts(String fullName, String firstName, String lastName) {
        CustomerName name = CustomerName.fromFullName(fullName);
        Customer customer = new Customer(fullName, "Address", null);

        assertThat(name.firstName()).isEqualTo(firstName);
        assertThat(name.lastName()).isEqualTo(lastName);
        assertThat(name.fullName()).isEqualTo(fullName);
        assertThat(customer.getFirstName()).isEqualTo(firstName);
        assertThat(customer.getLastName()).isEqualTo(lastName);
        assertThat(customer.getFullName()).isEqualTo(fullName);
    }

    private static Stream<Arguments> names() {
        return Stream.of(
                Arguments.of("Ivan Petrov", "Ivan", "Petrov"),
                Arguments.of("Ivan Sergeevich Petrov", "Ivan", "Sergeevich Petrov"),
                Arguments.of("Madonna", "Madonna", null),
                Arguments.of("Ivan  Petrov", "Ivan", " Petrov"),
                Arguments.of("Ivan ", "Ivan", ""),
                Arguments.of(" Ivan", "", "Ivan"),
                Arguments.of("Иван Петров", "Иван", "Петров"));
    }
}
