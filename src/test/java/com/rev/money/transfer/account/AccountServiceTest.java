package com.rev.money.transfer.account;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.rev.money.transfer.actor.AccountActor;
import com.rev.money.transfer.actor.AccountActor.DeleteAccount;
import com.rev.money.transfer.actor.AccountActor.GetAccount;
import com.rev.money.transfer.factory.AccountFactory;
import com.rev.money.transfer.model.Account;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;
import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.model.TransactionStatus;
import com.rev.money.transfer.service.AccountService;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

public class AccountServiceTest {

	private static final long ACCOUNT_NUMBER_1 = 1L;
	private static final long ACCOUNT_NUMBER_2 = 2L;

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

	private static ActorRef getTestAccount(Account account) {
		Props props = AccountActor.props(account);
		return system.actorOf(props);
	}

	@Test
	void testGetAccountReturnsExistingAccount() {
		new TestKit(system) {
			{
				Account expectedAccountInfo = new Account(ACCOUNT_NUMBER_1, ZERO);
				ActorRef account = getTestAccount(expectedAccountInfo);
				ActorRef accountService = getTestAccountService(Collections.singletonMap(ACCOUNT_NUMBER_1, account));
				accountService.tell(expectedAccountInfo, getRef());
				expectMsgClass(Success.class);
				accountService.tell(new GetAccount(ACCOUNT_NUMBER_1), getRef());
				expectMsg(expectedAccountInfo);
			}
		};
	}

	@Test
	void testGetAccountReturnsFailureIfAccountNotExisting() {
		new TestKit(system) {
			{
				ActorRef accountService = getTestAccountService(Collections.emptyMap());
				accountService.tell(new GetAccount(ACCOUNT_NUMBER_1), getRef());
				expectMsg(new Failure("Account " + ACCOUNT_NUMBER_1 + " not found"));
			}
		};
	}

	@Test
	void testCreateAccountReturnsSuccessIfAccountDoesNotExist() {
		new TestKit(system) {
			{
				ActorRef accountService = getTestAccountService(Collections.emptyMap());
				Account expectedAccountInfo = new Account(ACCOUNT_NUMBER_1, ZERO);
				accountService.tell(expectedAccountInfo, getRef());
				expectMsgClass(Success.class);
			}
		};
	}

	@Test
	void testCreateAccountReturnsFailureIfAccountExists() {
		new TestKit(system) {
			{
				Account expectedAccountInfo = new Account(ACCOUNT_NUMBER_1, ZERO);
				ActorRef accountActor = getTestAccount(expectedAccountInfo);
				ActorRef accountService = getTestAccountService(
						Collections.singletonMap(ACCOUNT_NUMBER_1, accountActor));
				accountService.tell(expectedAccountInfo, getRef());
				expectMsgClass(Success.class);
				accountService.tell(expectedAccountInfo, getRef());
				expectMsg(new Failure("Account " + ACCOUNT_NUMBER_1 + " already exists"));
			}
		};
	}

	@Test
    void testDeleteAccountReturnsSuccessIfAccountExists() {
        new TestKit(system) {{
            Account expectedAccountInfo = new Account(ACCOUNT_NUMBER_1, ZERO);
            ActorRef account = getTestAccount(expectedAccountInfo);
            ActorRef accountService = getTestAccountService(Collections.singletonMap(ACCOUNT_NUMBER_1, account));
            accountService.tell(expectedAccountInfo, getRef());
            expectMsgClass(Success.class);
            accountService.tell(new DeleteAccount(ACCOUNT_NUMBER_1), getRef());
            expectMsgClass(Success.class);
        }};
    }

    @Test
    void testDeleteAccountReturnsFailureIfAccountNotFound() {
        new TestKit(system) {{
            ActorRef accountService = getTestAccountService(Collections.emptyMap());
            accountService.tell(new DeleteAccount(ACCOUNT_NUMBER_1), getRef());
            expectMsg(new Failure("Account " + ACCOUNT_NUMBER_1 + " not found"));
        }};
    }

	@Test
	void testTransferReturnsSuccessIfAccountsExistAndSufficientBalance() {
		new TestKit(system) {
			{
				ActorRef accountService = prepareAccountServiceForTransfer(this, new Account(ACCOUNT_NUMBER_1, ONE),
						new Account(ACCOUNT_NUMBER_2, ONE));
				accountService.tell(
						new Transaction(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, ONE, TransactionStatus.NEW, null),
						getRef());
				expectMsgClass(Success.class);
				accountService.tell(new GetAccount(ACCOUNT_NUMBER_1), getRef());
				expectMsg(new Account(ACCOUNT_NUMBER_1, ZERO));
				accountService.tell(new GetAccount(ACCOUNT_NUMBER_2), getRef());
				expectMsg(new Account(ACCOUNT_NUMBER_2, BigDecimal.valueOf(2L)));
			}
		};
	}

	@Test
	void testTransferReturnsFailureIfAccountsExistButInsufficientBalance() {
		new TestKit(system) {
			{
				Account accountInfo1 = new Account(ACCOUNT_NUMBER_1, ZERO);
				ActorRef accountService = prepareAccountServiceForTransfer(this, accountInfo1,
						new Account(ACCOUNT_NUMBER_2, ONE));
				accountService.tell(
						new Transaction(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, ONE, TransactionStatus.NEW, null),
						getRef());
				expectMsg(new Failure("Insufficient balance to withdraw 1 from account " + accountInfo1));
			}
		};
	}

	@Test
	void testTransferReturnsFailureIfAccountsOneOfAccountsDoesNotExist() {
		new TestKit(system) {
			{
				ActorRef accountService = prepareAccountServiceForTransfer(this, null,
						new Account(ACCOUNT_NUMBER_2, ONE));
				accountService.tell(
						new Transaction(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, ONE, TransactionStatus.NEW, null),
						getRef());
				expectMsg(new Failure("Account 1 not found"));
				accountService = prepareAccountServiceForTransfer(this, new Account(ACCOUNT_NUMBER_1, ONE), null);
				accountService.tell(
						new Transaction(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, ONE, TransactionStatus.NEW, null),
						getRef());
				expectMsg(new Failure("Account 2 not found"));
			}
		};
	}

	@Test
	void testTransferReturnsFailureIfTargetAccountFailsOnDeposit() {
		new TestKit(system) {
			{
				Account acc1 = new Account(ACCOUNT_NUMBER_1, ONE);
				Account acc2 = new Account(ACCOUNT_NUMBER_2, ONE);
				ActorRef account1 = getTestAccount(acc1);
				ActorRef account2 = system.actorOf(OnDepositFailureAccount.props());
				ActorRef accountService = getTestAccountService(ImmutableMap.<Long, ActorRef>builder()
						.put(ACCOUNT_NUMBER_1, account1).put(ACCOUNT_NUMBER_2, account2).build());
				accountService.tell(acc1, getRef());
				expectMsgClass(Success.class);
				accountService.tell(acc2, getRef());
				expectMsgClass(Success.class);
				accountService.tell(
						new Transaction(1L, ACCOUNT_NUMBER_1, ACCOUNT_NUMBER_2, ONE, TransactionStatus.NEW, null),
						getRef());
				expectMsgClass(Failure.class);
				accountService.tell(new GetAccount(acc1.getAccountNumber()), getRef());
				expectMsg(acc1);
			}
		};
	}

	private ActorRef prepareAccountServiceForTransfer(TestKit testKit, Account acc1, Account acc2) {
		ActorRef account1 = getTestAccount(acc1);
		ActorRef account2 = getTestAccount(acc2);
		ActorRef accountService = getTestAccountService(ImmutableMap.<Long, ActorRef>builder()
				.put(ACCOUNT_NUMBER_1, account1).put(ACCOUNT_NUMBER_2, account2).build());
		if (acc1 != null) {
			accountService.tell(acc1, testKit.getRef());
			testKit.expectMsgClass(Success.class);
		}
		if (acc2 != null) {
			accountService.tell(acc2, testKit.getRef());
			testKit.expectMsgClass(Success.class);
		}
		return accountService;
	}

	private ActorRef getTestAccountService(Map<Long, ActorRef> accountsById) {
		Props props = AccountService.props(new TestAccountFactory(accountsById), Duration.ofSeconds(1));
		return system.actorOf(props);
	}

	static class OnDepositFailureAccount extends AbstractActor {

		static Props props() {
			return Props.create(OnDepositFailureAccount.class, OnDepositFailureAccount::new);
		}

		@Override
		public Receive createReceive() {
			return receiveBuilder().match(AccountActor.Deposit.class,
					deposit -> sender().tell(new Failure("Always fail on that"), self())).build();
		}
	}

	static class TestAccountFactory extends AccountFactory {

		private Map<Long, ActorRef> accountsById;

		TestAccountFactory(Map<Long, ActorRef> accountsById) {
			this.accountsById = accountsById;
		}

		@Override
		public ActorRef get(ActorContext context, Account accountInfo) {
			return accountsById.get(accountInfo.getAccountNumber());
		}
	}
}
