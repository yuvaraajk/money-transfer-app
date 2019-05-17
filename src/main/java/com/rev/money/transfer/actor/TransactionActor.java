package com.rev.money.transfer.actor;

import java.io.Serializable;

import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.model.TransactionStatus;

import akka.actor.AbstractActor;
import akka.actor.Props;
import lombok.Data;

public class TransactionActor extends AbstractActor {

	private Transaction transactionInfo;

	private TransactionActor(Transaction transactionInfo) {
		this.transactionInfo = transactionInfo;
	}

	public static Props props(Transaction transactionInfo) {
		return Props.create(TransactionActor.class, () -> new TransactionActor(transactionInfo));
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(GetTransaction.class, this::onGetTransactionInfo)
				.match(ChangeStatus.class, this::onChangeStatus).build();
	}

	private void onGetTransactionInfo(GetTransaction getTransaction) {
		sender().tell(transactionInfo, self());
	}

	private void onChangeStatus(ChangeStatus changeStatus) {
		TransactionStatus status = changeStatus.status;
		transactionInfo = new Transaction(transactionInfo.getId(), transactionInfo.getRemitterAccountId(),
				transactionInfo.getBeneficieryAccountId(), transactionInfo.getAmount(), status,
				transactionInfo.getRemarks());
		sender().tell(transactionInfo, self());
	}

	@Data
	public static class ChangeStatus implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -7119184910731755253L;
		private final TransactionStatus status;
	}

	@Data
	public static class GetTransaction implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -5489508581791470711L;
		private final long id;
	}
}
