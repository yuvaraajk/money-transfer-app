package com.rev.money.transfer.actor;

import java.io.Serializable;
import java.math.BigDecimal;

import com.rev.money.transfer.model.Account;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import lombok.Data;

public class AccountActor extends AbstractLoggingActor {

	private Account account;

	public AccountActor(Account account) {
		this.account = account;
	}

	public static Props props(Account account) {
		return Props.create(AccountActor.class, () -> new AccountActor(account));
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(GetAccount.class, this::onGetAccount)
				.match(Withdraw.class, withdraw -> this.withdraw(withdraw.getAmount()))
				.match(Deposit.class, deposit -> this.deposit(deposit.getAmount()))
				.match(DeleteAccount.class, this::onDeleteAccount).build();
	}

	@Data
	public static class GetAccount implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -1865819373975609281L;

		private final long accountNumber;
	}

	private void onGetAccount(GetAccount getAccount) {
		sender().tell(account, getSelf());
	}

	@Data
	public static class Withdraw implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7321232735063503252L;
		private final BigDecimal amount;
	}
	
//
//	@Data
//	public static class CashDeposit implements Serializable {
//		/**
//		 * 
//		 */
//		private static final long serialVersionUID = -7458067013887872515L;
//		private final BigDecimal amount;
//	}

	@Data
	public static class Deposit implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1290932509175161923L;
		private final BigDecimal amount;
	}

	private void withdraw(BigDecimal amount) {
		BigDecimal balance = account.getBalance();
		if (amount.compareTo(balance) > 0) {
			notifyInsufficientBalance(amount);
		} else {
			updateBalance(balance.subtract(amount), "Withdraw");
		}
	}

	private void notifyInsufficientBalance(BigDecimal amount) {
		String errorMsg = "Insufficient balance to withdraw " + amount + " from account " + account;
		log().info(errorMsg);
		sender().tell(new Failure(errorMsg), self());
	}
	
//	private void cashDeposit(BigDecimal amount) {
//		updateBalance(amount, "Deposit");
//	}	

	private void deposit(BigDecimal amount) {
		BigDecimal balance = account.getBalance();
		updateBalance(balance.add(amount), "Deposit");
	}

	private void updateBalance(BigDecimal newBalance, String operation) {
		account = new Account(account.getAccountNumber(), newBalance);
		log().info("{} succeeded for {}", operation, account);
		sender().tell(new Success(), self());
	}

	@Data
	public static class DeleteAccount implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 6413900101369106107L;

		private final long accountNumber;
	}

	private void onDeleteAccount(DeleteAccount deleteAccount) {
		sender().tell(account, getSelf());
	}

}
