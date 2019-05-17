package com.rev.money.transfer.mapper;

import java.util.function.Function;

import com.rev.money.transfer.dto.AccountDTO;
import com.rev.money.transfer.model.Account;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AccountMapper {

	public static class AccountToDtoMapper implements Function<Account, AccountDTO> {

		@Override
		public AccountDTO apply(Account account) {
			return new AccountDTO(account.getAccountNumber(), account.getBalance());
		}
	}

	public static class AccountDtoToModelMapper implements Function<AccountDTO, Account> {

		@Override
		public Account apply(AccountDTO accountDto) {
			return new Account(accountDto.getAccountNumber(), accountDto.getBalance());
		}
	}
}
