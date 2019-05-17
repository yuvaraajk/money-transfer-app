package com.rev.money.transfer.model;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Value;

@Value
public class Transaction implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8036067932985280204L;

	private Long id;
	private Long remitterAccountId;
	private Long beneficieryAccountId;
	private BigDecimal amount;
	private TransactionStatus status;
	private String remarks;

}
