package com.rev.money.transfer.transaction;

import static java.math.BigDecimal.TEN;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.rev.money.transfer.actor.TransactionActor;
import com.rev.money.transfer.factory.TransactionFactory;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;
import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.model.TransactionStatus;
import com.rev.money.transfer.service.TransactionService;
import com.rev.money.transfer.service.TransactionService.TransactionRolledBack;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

public class TransactionServiceTest {

	private static final long TRANS_ID = 1L;

	private static ActorSystem system;

	@BeforeAll
	static void setup() {
		system = ActorSystem.create();
	}

	@AfterAll
	static void shutdown() {
		TestKit.shutdownActorSystem(system);
		system = null;
	}

	private static ActorRef getTestTransaction(Transaction transactionInfo) {
		Props props = TransactionActor.props(transactionInfo);
		return system.actorOf(props);
	}

	@Test
	void testCreateTransactionReturnsCommittedTransactionIfTransferSucceeded() {
		new TestKit(system) {
			{
				Transaction trInfo = new Transaction(TRANS_ID, 1L, 2L, TEN, TransactionStatus.NEW, null);
				ActorRef tr1 = getTestTransaction(trInfo);
				ActorRef transactionService = getTestTransactionService(getTestAccountService(false),
						Collections.singletonMap(TRANS_ID, tr1));
				transactionService.tell(trInfo, getRef());
				expectMsg(new Transaction(TRANS_ID, 1L, 2L, TEN, TransactionStatus.SUCCESS, null));
			}
		};
	}

	@Test
	void testCreateTransactionReturnsTransactionRolledBackIfTransferFailed() {
		new TestKit(system) {
			{
				Transaction trInfo = new Transaction(TRANS_ID, 1L, 2L, TEN, TransactionStatus.NEW, null);
				ActorRef tr1 = getTestTransaction(trInfo);
				ActorRef transactionService = getTestTransactionService(getTestAccountService(true),
						Collections.singletonMap(TRANS_ID, tr1));
				transactionService.tell(trInfo, getRef());
				expectMsg(new TransactionRolledBack(
						new Transaction(TRANS_ID, 1L, 2L, TEN, TransactionStatus.FAIL, null), trInfo.toString()));
			}
		};
	}

	private ActorRef getTestTransactionService(ActorRef accountService, Map<Long, ActorRef> transactionsById) {
		Props props = TransactionService.props(accountService, new TestTransactionFactory(transactionsById),
				Duration.ofMillis(1));
		return system.actorOf(props);
	}

	ActorRef getTestAccountService(boolean failOnTransfer) {
		Props props = TestAccountService.props(failOnTransfer);
		return system.actorOf(props);
	}

	static class TestAccountService extends AbstractActor {

		private boolean failOnTransfer;

		TestAccountService(boolean failOnTransfer) {
			this.failOnTransfer = failOnTransfer;
		}

		static Props props(boolean failOnTransfer) {
			return Props.create(TestAccountService.class, () -> new TestAccountService(failOnTransfer));
		}

		@Override
		public Receive createReceive() {
			return receiveBuilder().match(Transaction.class, x -> {
				if (failOnTransfer) {
					sender().tell(new Failure(x.toString()), self());
				} else {
					sender().tell(new Success(), self());
				}
			}).build();

		}
	}

	static class TestTransactionFactory extends TransactionFactory {
		
		private Map<Long, ActorRef> transactionsById;

		TestTransactionFactory(Map<Long, ActorRef> transactionsById) {
			this.transactionsById = transactionsById;
		}

		@Override
		public ActorRef get(ActorContext context, Transaction transactionInfo) {
			return transactionsById.get(transactionInfo.getId());
		}
	}
}
