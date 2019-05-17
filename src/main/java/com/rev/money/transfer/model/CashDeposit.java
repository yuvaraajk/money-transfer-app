package com.rev.money.transfer.model;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Value;

@Value
public class CashDeposit implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 9003176160979567287L;
	
	private Long id;
	private Long accountNumber;
	private BigDecimal amount;

}
