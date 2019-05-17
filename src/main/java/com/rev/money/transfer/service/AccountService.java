package com.rev.money.transfer.service;

import static akka.pattern.Patterns.ask;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import com.rev.money.transfer.actor.AccountActor;
import com.rev.money.transfer.actor.AccountActor.DeleteAccount;
import com.rev.money.transfer.factory.AccountFactory;
import com.rev.money.transfer.model.Account;
import com.rev.money.transfer.model.Customer;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;
import com.rev.money.transfer.service.TransactionService.CashDepositTransaction;
import com.rev.money.transfer.model.Transaction;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;

public class AccountService extends AbstractLoggingActor {

	private final AccountFactory accountFactory;
	private final Map<Long, ActorRef> accountsById = new HashMap<>();
	private final Duration timeout;

	private AccountService(AccountFactory accountFactory, Duration timeout) {
		this.accountFactory = accountFactory;
		this.timeout = timeout;
	}

	public static Props props(AccountFactory accountFactory, Duration timeout) {
		return Props.create(AccountService.class, () -> new AccountService(accountFactory, timeout));
	}

	public static Props props(AccountFactory accountFactory) {
		return AccountService.props(accountFactory, Duration.ofSeconds(1));
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(Account.class, this::onAccountInfo)
				.match(Customer.class, this::onCustomer)
				.match(AccountActor.GetAccount.class, this::onGetAccount)
				.match(DeleteAccount.class, this::onDeleteAccount)
				.match(Transaction.class, this::onTransfer)
				.match(CashDepositTransaction.class, this::onDeposit).build();
	}
	
	private void onCustomer(Customer customer) {
		Account account = new Account(customer.getId(), BigDecimal.ZERO);
		long id = account.getAccountNumber();
		if (accountsById.containsKey(id)) {
			replyAccountAlreadyExists(id);
			return;
		}
		ActorRef accountActor = accountFactory.get(context(), account);
		accountsById.put(id, accountActor);
		log().info("Account {} created", id);
		sender().tell(new Success(), getSelf());
	}

	private void onAccountInfo(Account account) {
		long id = account.getAccountNumber();
		if (accountsById.containsKey(id)) {
			replyAccountAlreadyExists(id);
			return;
		}
		createAccount(account);
	}

	private void replyAccountAlreadyExists(long id) {
		String errorMsg = "Account " + id + " already exists";
		log().info(errorMsg);
		sender().tell(new Failure(errorMsg), self());
	}

	private void createAccount(Account account) {
		long id = account.getAccountNumber();
		ActorRef actorRef = accountFactory.get(context(), account);
		accountsById.put(id, actorRef);
		log().info("Account {} created", id);
		sender().tell(new Success(), self());
	}

	private void onDeleteAccount(DeleteAccount deleteAccount) {
		long id = deleteAccount.getAccountNumber();
		ActorRef account = accountsById.remove(id);
		if (account == null) {
			replyAccountNotFound(id);
			return;
		}
		context().stop(account);
		replyAccountDeleted(id);
	}

	private void replyAccountDeleted(long id) {
		log().info("Account {} deleted", id);
		sender().tell(new Success(), self());
	}

	private void onGetAccount(AccountActor.GetAccount getAccount) {
		long id = getAccount.getAccountNumber();
		ActorRef accountActor = accountsById.get(id);
		if (accountActor == null) {
			replyAccountNotFound(id);
			return;
		}
		forwardGetAccount(accountActor, getAccount);
	}

	private void forwardGetAccount(ActorRef account, AccountActor.GetAccount getAccount) {
		ActorRef replyTo = sender();
		ask(account, getAccount, timeout).thenAcceptAsync(accountInfo -> replyTo.tell(accountInfo, self()));
	}

	private void onDeposit(CashDepositTransaction accountDepositTransaction) {
		Transaction transaction = accountDepositTransaction.getTransaction();
		log().info("Processing deposit transaction {}", transaction);
		ActorRef selfAccount = getAccount(transaction.getRemitterAccountId(), transaction);
		if (selfAccount == null)
			return;
		transfer(transaction, selfAccount);
	}

	private void transfer(Transaction transaction, ActorRef selfAccount) {
		ActorRef replyTo = sender();
		cashDeposit(selfAccount, transaction.getAmount()).thenAcceptAsync(response -> {
			if (response instanceof Failure) {
				replyTransferFailed(transaction, (Failure) response, replyTo);
			} else {
				log().info("Transaction {} succeeded", transaction.getId());
				replyTo.tell(new Success(), self());
			}
		});
	}

	private CompletionStage<Object> cashDeposit(ActorRef selfAccount, BigDecimal amount) {
		return ask(selfAccount, new AccountActor.Deposit(amount), timeout);
	}

	private void onTransfer(Transaction transactionInfo) {
		log().info("Processing transaction {}", transactionInfo);

		ActorRef srcAccount = getAccount(transactionInfo.getRemitterAccountId(), transactionInfo);
		if (srcAccount == null)
			return;

		ActorRef targetAccount = getAccount(transactionInfo.getBeneficieryAccountId(), transactionInfo);
		if (targetAccount == null)
			return;

		transfer(transactionInfo, srcAccount, targetAccount);
	}

	private ActorRef getAccount(long srcAccountId, Transaction transactionInfo) {
		ActorRef srcAccount = accountsById.get(srcAccountId);
		if (srcAccount == null) {
			replyTransactionWithNonExistingAccount(srcAccountId, transactionInfo);
			return null;
		}
		return srcAccount;
	}

	private void replyTransactionWithNonExistingAccount(long targetAccountId, Transaction transactionInfo) {
		log().warning("Transaction {} failed", transactionInfo);
		replyAccountNotFound(targetAccountId);
	}

	private void replyAccountNotFound(long id) {
		String errorMsg = "Account " + id + " not found";
		log().warning(errorMsg);
		sender().tell(new Failure(errorMsg), self());
	}

	private void transfer(Transaction transactionInfo, ActorRef srcAccount, ActorRef targetAccount) {
		ActorRef replyTo = sender();
		withdrawSrcAccount(srcAccount, transactionInfo.getAmount()).thenAcceptAsync(responseFromSrc -> {
			if (responseFromSrc instanceof Failure) {
				replyTransferFailed(transactionInfo, (Failure) responseFromSrc, replyTo);
			} else {
				depositTargetAccount(transactionInfo, srcAccount, targetAccount, replyTo);
			}
		});
	}

	private CompletionStage<Object> withdrawSrcAccount(ActorRef srcAccount, BigDecimal amount) {
		return ask(srcAccount, new AccountActor.Withdraw(amount), timeout);
	}

	private void depositTargetAccount(Transaction transactionInfo, ActorRef srcAccount, ActorRef targetAccount,
			ActorRef replyTo) {
		ask(targetAccount, new AccountActor.Deposit(transactionInfo.getAmount()), timeout)
				.thenAcceptAsync(depositResponse -> {
					if (depositResponse instanceof Failure) {
						replyTransferFailed(transactionInfo, (Failure) depositResponse, replyTo);
						revertSrcAccountBalance(transactionInfo, srcAccount);
					} else {
						log().info("Transaction {} succeeded", transactionInfo.getId());
						replyTo.tell(new Success(), self());
					}
				});
	}

	private void replyTransferFailed(Transaction transactionInfo, Failure failure, ActorRef replyTo) {
		log().warning("Transaction {} failed with reason: {}", transactionInfo.getId(), failure.getMessage());
		replyTo.tell(failure, self());
	}

	private void revertSrcAccountBalance(Transaction transactionInfo, ActorRef srcAccount) {
		srcAccount.tell(new AccountActor.Deposit(transactionInfo.getAmount()), self());
	}

}
