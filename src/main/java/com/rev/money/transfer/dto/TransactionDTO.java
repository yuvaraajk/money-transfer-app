package com.rev.money.transfer.dto;

import java.math.BigDecimal;

import com.rev.money.transfer.model.TransactionStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDTO {

	private Long id;
	private Long remitterAccountId;
	private Long beneficieryAccountId;
	private BigDecimal amount;
	private TransactionStatus status;
	private String remarks;

}
