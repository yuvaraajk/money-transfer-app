package com.rev.money.transfer.mapper;

import java.util.function.Function;

import com.rev.money.transfer.dto.CashDepositDTO;
import com.rev.money.transfer.model.CashDeposit;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CashDepositMapper {

	public static class CashDepositToDtoMapper implements Function<CashDeposit, CashDepositDTO> {

		@Override
		public CashDepositDTO apply(CashDeposit accountDeposit) {
			return new CashDepositDTO(accountDeposit.getId(), accountDeposit.getAccountNumber(), accountDeposit.getAmount());
		}
	}

	public static class CashDepositDtoToModelMapper implements Function<CashDepositDTO, CashDeposit> {

		@Override
		public CashDeposit apply(CashDepositDTO t) {
			return new CashDeposit(t.getId(), t.getAccountNumber(), t.getAmount());
		}
	}

}
