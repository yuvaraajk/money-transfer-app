package com.rev.money.transfer.account;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.rev.money.transfer.actor.AccountActor;
import com.rev.money.transfer.actor.AccountActor.Deposit;
import com.rev.money.transfer.actor.AccountActor.GetAccount;
import com.rev.money.transfer.actor.AccountActor.Withdraw;
import com.rev.money.transfer.model.Account;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

public class AccountActorTest {

	private static final Long ACCOUNT_NUMBER = 1L;

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

	private static ActorRef getTestAccount(Account accountInfo) {
		Props props = AccountActor.props(accountInfo);
		return system.actorOf(props);
	}

	@Test
	void testGetAccountReturnsExpectedAccountInfo() {
		new TestKit(system) {
			{
				Account expectedAccountInfo = new Account(ACCOUNT_NUMBER, TEN);
				ActorRef account = getTestAccount(expectedAccountInfo);
				account.tell(new GetAccount(ACCOUNT_NUMBER), getRef());
				expectMsg(expectedAccountInfo);
			}
		};
	}

	@Test
	void testWithdrawReturnsSuccessAndChangesAccountState() {
		new TestKit(system) {
			{
				Account accountInfo = new Account(ACCOUNT_NUMBER, TEN);
				ActorRef account = getTestAccount(accountInfo);
				account.tell(new Withdraw(ONE), getRef());
				expectMsg(new Success());
				account.tell(new GetAccount(ACCOUNT_NUMBER), getRef());
				expectMsg(new Account(ACCOUNT_NUMBER, new BigDecimal(9)));
			}
		};
	}

	@Test
	void testWithdrawReturnsFailureWhenInsufficientBalance() {
		new TestKit(system) {
			{
				Account accountInfo = new Account(ACCOUNT_NUMBER, ZERO);
				ActorRef account = getTestAccount(accountInfo);
				account.tell(new Withdraw(ONE), getRef());
				expectMsg(new Failure("Insufficient balance to withdraw " + ONE + " from account " + accountInfo));
			}
		};
	}

	@Test
	void testDepositReturnsSuccessAndChangesAccountState() {
		new TestKit(system) {
			{
				Account accountInfo = new Account(ACCOUNT_NUMBER, ZERO);
				ActorRef account = getTestAccount(accountInfo);
				account.tell(new Deposit(ONE), getRef());
				expectMsg(new Success());
				account.tell(new GetAccount(ACCOUNT_NUMBER), getRef());
				expectMsg(new Account(1L, ONE));
			}
		};
	}
}
