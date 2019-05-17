package com.rev.money.transfer.model;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Customer implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8995708612688942720L;

	private final Long id;
	private final String name;
	private final Long contactNumber;
	private final String email;
	private final Integer zipCode;
	private final Long accountNumber;

}
