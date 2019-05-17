package com.rev.money.transfer.transaction;

import static java.math.BigDecimal.TEN;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.rev.money.transfer.actor.TransactionActor;
import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.model.TransactionStatus;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

public class TransactionTest {

	private final static long TRANSACTION_ID = 1L;
	private final static long REMITTER_ACCOUNT = 1L;
	private final static long BENEF_ACCOUNT = 2L;

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

	private static ActorRef getTestTransaction(Transaction transaction) {
		Props props = TransactionActor.props(transaction);
		return system.actorOf(props);
	}

	@Test
	void testGetAccountReturnsExpectedAccountInfo() {
		new TestKit(system) {
			{
				Transaction expectedTransaction = new Transaction(TRANSACTION_ID, REMITTER_ACCOUNT, BENEF_ACCOUNT, TEN,
						TransactionStatus.NEW, null);
				ActorRef account = getTestTransaction(expectedTransaction);
				account.tell(new TransactionActor.GetTransaction(TRANSACTION_ID), getRef());
				expectMsg(expectedTransaction);
			}
		};
	}

	@ParameterizedTest
	@EnumSource(TransactionStatus.class)
	void testChangeStatusReturnsExpectedTransactionInfo(TransactionStatus status) {
		new TestKit(system) {
			{
				ActorRef transactionActor = getTestTransaction(new Transaction(TRANSACTION_ID, REMITTER_ACCOUNT,
						BENEF_ACCOUNT, TEN, TransactionStatus.NEW, null));
				transactionActor.tell(new TransactionActor.ChangeStatus(status), getRef());
				Transaction expectedTransaction = new Transaction(TRANSACTION_ID, REMITTER_ACCOUNT, BENEF_ACCOUNT, TEN,
						status, null);
				expectMsg(expectedTransaction);
			}
		};
	}
}
