package com.rev.money.transfer.service;

import static akka.pattern.Patterns.ask;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.rev.money.transfer.actor.TransactionActor;
import com.rev.money.transfer.factory.TransactionFactory;
import com.rev.money.transfer.model.CashDeposit;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;
import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.model.TransactionStatus;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import lombok.Data;

public class TransactionService extends AbstractLoggingActor {

	// In-memory store for CRUD operations
	private final Map<Long, ActorRef> transactionsById = new HashMap<>();
	private final TransactionFactory transactionFactory;
	private final ActorRef accountService;
	private final Duration timeout;

	private TransactionService(ActorRef accountService, TransactionFactory transactionFactory, Duration timeout) {
		this.accountService = accountService;
		this.transactionFactory = transactionFactory;
		this.timeout = timeout;
	}

	public static Props props(ActorRef accountService, TransactionFactory transactionFactory, Duration timeout) {
		return Props.create(TransactionService.class,
				() -> new TransactionService(accountService, transactionFactory, timeout));
	}

	/**
	 * Mail box to receive the messages
	 */
	@Override
	public Receive createReceive() {
		return receiveBuilder().match(Transaction.class, this::doTransaction)
							   .match(TransactionActor.GetTransaction.class, this::onGetTransactionInfo)
							   .match(DeleteTransaction.class, this::onDeleteTransaction)
							   .match(CashDeposit.class, this::doCashDeposit)
							   .build();
	}
	
	private void doCashDeposit(CashDeposit cashDeposit) {
		log().info("In cash deposit");
		long id = cashDeposit.getId();
		Transaction transaction = new Transaction(id, cashDeposit.getAccountNumber(), null,
				cashDeposit.getAmount(), TransactionStatus.NEW, "Cash Deposit");
		CashDepositTransaction accountDepositTrans = new CashDepositTransaction(id, transaction);
		depositTransaction(accountDepositTrans);
	}
	
	private void depositTransaction(CashDepositTransaction accountDeposit) {
		long transactionId = accountDeposit.getId();
		if (transactionsById.containsKey(transactionId)) {
			replyTransactionAlreadyExists(transactionId);
			return;
		}
		ActorRef transactionActor = transactionFactory.get(context(), accountDeposit.getTransaction());
		transactionsById.put(transactionId, transactionActor);
		depositTransaction(accountDeposit, transactionActor);
	}
	
	private void depositTransaction(CashDepositTransaction accountDeposit, ActorRef transactionActor) {
		ActorRef replyTo = sender();
		ask(accountService, accountDeposit, timeout).thenAcceptAsync(
				transferResponse -> handleTransferResponse(transactionActor, transferResponse, replyTo));
	}

	/************************************************************************************************************
	 * 				Perform the Transaction from Remitter and Beneficiary account								*
	 ************************************************************************************************************/

	/**
	 * 
	 * 
	 * @param transaction
	 */
	private void doTransaction(Transaction transaction) {
		log().info("In Money Transfer");
		long transactionId = transaction.getId();
		if (transactionsById.containsKey(transactionId)) {
			replyTransactionAlreadyExists(transactionId);
			return;
		}
		ActorRef transactionActor = transactionFactory.get(context(), transaction);
		transactionsById.put(transactionId, transactionActor);
		doTransaction(transaction, transactionActor);
	}

	private void replyTransactionAlreadyExists(long transactionId) {
		String errorMsg = "Transaction " + transactionId + " already been processed";
		log().warning(errorMsg);
		sender().tell(new Failure(errorMsg), sender());
	}

	private void doTransaction(Transaction transaction, ActorRef transactionActor) {
		ActorRef replyTo = sender();
		ask(accountService, transaction, timeout).thenAcceptAsync(
				transferResponse -> handleTransferResponse(transactionActor, transferResponse, replyTo));
	}

	private void handleTransferResponse(ActorRef transactionActor, Object transferResponse, ActorRef replyTo) {
		if (transferResponse instanceof Failure) {
			rollbackTransaction(transactionActor, replyTo, (Failure) transferResponse);
		} else {
			commitTransaction(transactionActor, replyTo);
		}
	}

	private void rollbackTransaction(ActorRef transactionActor, ActorRef replyTo, Failure response) {
		ask(transactionActor, new TransactionActor.ChangeStatus(TransactionStatus.FAIL), timeout).thenAcceptAsync(
				updatedTransaction -> replyTransactionRolledBack(replyTo, response, (Transaction) updatedTransaction));
	}

	private void replyTransactionRolledBack(ActorRef replyTo, Failure response, Transaction updatedTransaction) {
		replyTo.tell(new TransactionRolledBack(updatedTransaction, response.getMessage()), replyTo);
	}

	private void commitTransaction(ActorRef transactionActor, ActorRef replyTo) {
		ask(transactionActor, new TransactionActor.ChangeStatus(TransactionStatus.SUCCESS), timeout)
				.thenAcceptAsync(updatedTransaction -> replyTo.tell(updatedTransaction, replyTo));
	}
	
	/************************************************************************************************************
	 * 										Get the Transaction by id											*
	 ************************************************************************************************************/

	/**
	 * Get transaction by transactionId
	 * 
	 * @param getTransaction
	 */
	private void onGetTransactionInfo(TransactionActor.GetTransaction getTransaction) {
		long id = getTransaction.getId();
		ActorRef transactionActor = transactionsById.get(id);
		if (transactionActor == null) {
			replyTransactionNotFound(id);
			return;
		}
		forwardGetTransaction(transactionActor, getTransaction);
	}

	private void replyTransactionNotFound(long transactionId) {
		String errorMsg = "Transaction " + transactionId + " does not exist";
		log().warning(errorMsg);
		sender().tell(new Failure(errorMsg), sender());
	}

	private void forwardGetTransaction(ActorRef transactionActor, TransactionActor.GetTransaction getTransaction) {
		ActorRef replyTo = sender();
		ask(transactionActor, getTransaction, timeout)
				.thenAcceptAsync(transactionInfo -> replyTo.tell(transactionInfo, self()));
	}
	
	/************************************************************************************************************
	 * 								Delete the transaction by transaction ID									*
	 ************************************************************************************************************/

	private void onDeleteTransaction(DeleteTransaction deleteTransaction) {
		long transactionId = deleteTransaction.getId();
		ActorRef transactionActor = transactionsById.remove(transactionId);
		if (transactionActor == null) {
			replyTransactionNotFound(transactionId);
			return;
		}
		replyTransactionDeleted(transactionId, transactionActor);
	}

	private void replyTransactionDeleted(long transactionId, ActorRef transactionActor) {
		context().stop(transactionActor);
		log().info("Transaction {} deleted", transactionId);
		sender().tell(new Success(), sender());
	}

	@Data
	public static class DeleteTransaction implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7758880363900608050L;

		private final long id;
	}

	@Data
	public static class TransactionRolledBack implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 8404788482871859850L;

		private final Transaction transactionInfo;
		private final String reason;
	}
	
	@Data
	public static class CashDepositTransaction implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 2398162149138320856L;
		
		private final long id;
		private final Transaction transaction;
	}

}
