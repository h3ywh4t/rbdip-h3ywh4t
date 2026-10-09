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
        customerRepository.insertIfAbsent(fullName, address, phone);
        return customerRepository.findByFullNameAndAddressAndPhone(fullName, address, phone)
                .orElseThrow(() -> new IllegalStateException("customer was not persisted"));
    }
}
