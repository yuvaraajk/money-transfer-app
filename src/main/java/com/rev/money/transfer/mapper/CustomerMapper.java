package com.rev.money.transfer.mapper;

import java.util.function.Function;

import com.rev.money.transfer.dto.CustomerDTO;
import com.rev.money.transfer.model.Customer;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CustomerMapper {

	public static class CustomerToDtoMapper implements Function<Customer, CustomerDTO> {

		@Override
		public CustomerDTO apply(Customer customer) {
			return new CustomerDTO(customer.getId(), customer.getName(), customer.getContactNumber(),
					customer.getEmail(), customer.getZipCode(), customer.getAccountNumber());
		}

	}

	public static class CustomerDtoToModelMapper implements Function<CustomerDTO, Customer> {

		@Override
		public Customer apply(CustomerDTO customerDto) {
			return Customer.builder().id(customerDto.getId()).name(customerDto.getName())
					.accountNumber(customerDto.getAccountNumber()).email(customerDto.getEmail())
					.contactNumber(customerDto.getContactNumber()).zipCode(customerDto.getZipCode()).build();
		}

	}

}
