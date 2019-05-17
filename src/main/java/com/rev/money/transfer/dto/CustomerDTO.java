package com.rev.money.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDTO {

	private Long id;
	private String name;
	private Long contactNumber;
	private String email;
	private Integer zipCode;
	private Long accountNumber;

}
