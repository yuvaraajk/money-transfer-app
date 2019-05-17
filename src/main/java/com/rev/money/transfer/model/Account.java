package com.rev.money.transfer.model;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Value;

@Value
public class Account implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6737825389075270724L;

	private Long accountNumber;
	private BigDecimal balance;

}
