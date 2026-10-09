package com.rbdip.bookstore.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {

    private static final int ADDRESS_LENGTH = 500;
    private static final int PHONE_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(length = ADDRESS_LENGTH)
    private String address;

    @Column(length = PHONE_LENGTH)
    private String phone;

    protected Customer() {
    }

    public Customer(String fullName, String address, String phone) {
        CustomerName name = CustomerName.fromFullName(fullName);
        this.firstName = name.firstName();
        this.lastName = name.lastName();
        this.address = address;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return new CustomerName(firstName, lastName).fullName();
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }
}
