package com.rev.money.transfer.transaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.rev.money.transfer.actor.AccountActor;
import com.rev.money.transfer.factory.AccountFactory;
import com.rev.money.transfer.factory.TransactionFactory;
import com.rev.money.transfer.model.Account;
import com.rev.money.transfer.model.MessageStatus;
import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.model.TransactionStatus;
import com.rev.money.transfer.service.AccountService;
import com.rev.money.transfer.service.TransactionService;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;

public class ConcurrentTest {

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

	@Test
	void testTransferIsThreadSafe() {
		new TestKit(system) {
			{
				int numThreads = 100;
				Account remitterAccount = new Account(1L, BigDecimal.valueOf(numThreads));
				Account benefAccount = new Account(2L, BigDecimal.ZERO);
				ActorRef accountService = system
						.actorOf(AccountService.props(new AccountFactory(), Duration.ofSeconds(1)));
				ActorRef transactionService = system.actorOf(
						(TransactionService.props(accountService, new TransactionFactory(), Duration.ofSeconds(1))));

				accountService.tell(remitterAccount, getRef());
				expectMsgClass(MessageStatus.Success.class);

				accountService.tell(benefAccount, getRef());
				expectMsgClass(MessageStatus.Success.class);

				ExecutorService service = Executors.newFixedThreadPool(numThreads);
				final CountDownLatch latch = new CountDownLatch(1);
				AtomicLong nextId = new AtomicLong();

				for (int i = 0; i < numThreads; i++) {
					service.submit(() -> {
						try {
							Transaction transactionInfo = new Transaction(nextId.incrementAndGet(), 1L, 2L,
									BigDecimal.ONE, TransactionStatus.NEW, null);
							latch.await();
							transactionService.tell(transactionInfo, getRef());
						} catch (InterruptedException ignored) {
						}
					});
				}

				latch.countDown();
				receiveN(numThreads);

				accountService.tell(new AccountActor.GetAccount(1L), getRef());
				expectMsg(new Account(1L, BigDecimal.ZERO));

				accountService.tell(new AccountActor.GetAccount(2L), getRef());
				expectMsg(new Account(2L, BigDecimal.valueOf(numThreads)));
			}
		};
	}
}
