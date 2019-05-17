package com.rev.money.transfer.mapper;

import java.util.function.Function;

import com.rev.money.transfer.dto.TransactionDTO;
import com.rev.money.transfer.model.Transaction;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TransactionMapper {

	public static class TransactionToDtoMapper implements Function<Transaction, TransactionDTO> {

		@Override
		public TransactionDTO apply(Transaction transaction) {
			return new TransactionDTO(transaction.getId(), transaction.getRemitterAccountId(),
					transaction.getBeneficieryAccountId(), transaction.getAmount(), transaction.getStatus(),
					transaction.getRemarks());
		}

	}

	public static class TransactionDtoToModelMapper implements Function<TransactionDTO, Transaction> {

		@Override
		public Transaction apply(TransactionDTO transactionDto) {
			return new Transaction(transactionDto.getId(), transactionDto.getRemitterAccountId(),
					transactionDto.getBeneficieryAccountId(), transactionDto.getAmount(), transactionDto.getStatus(),
					transactionDto.getRemarks());
		}

	}

}
