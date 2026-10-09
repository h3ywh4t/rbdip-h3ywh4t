package com.rbdip.bookstore.customer;

public record CustomerName(String firstName, String lastName) {

    public static CustomerName fromFullName(String fullName) {
        int separator = fullName.indexOf(' ');
        if (separator < 0) {
            return new CustomerName(fullName, null);
        }
        return new CustomerName(fullName.substring(0, separator), fullName.substring(separator + 1));
    }

    public String fullName() {
        return lastName == null ? firstName : firstName + " " + lastName;
    }
}
