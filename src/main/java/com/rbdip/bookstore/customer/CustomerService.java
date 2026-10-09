package com.rbdip.bookstore.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer findOrCreate(String fullName, String address, String phone) {
        CustomerName name = CustomerName.fromFullName(fullName);
        customerRepository.insertIfAbsent(name.firstName(), name.lastName(), address, phone);
        return customerRepository.findByFirstNameAndLastNameAndAddressAndPhone(
                name.firstName(), name.lastName(), address, phone)
                .orElseThrow(() -> new IllegalStateException("customer was not persisted"));
    }
}
