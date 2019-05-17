package com.rev.money.transfer.customer;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.rev.money.transfer.actor.CustomerActor;
import com.rev.money.transfer.actor.CustomerActor.DeleteCustomer;
import com.rev.money.transfer.actor.CustomerActor.GetCustomer;
import com.rev.money.transfer.factory.CustomerFactory;
import com.rev.money.transfer.model.Customer;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;
import com.rev.money.transfer.service.CustomerService;

import akka.actor.AbstractActor;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

public class CustomerServiceTest {

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

	private static ActorRef getTestCustomer(Customer customer) {
		Props props = CustomerActor.props(customer);
		return system.actorOf(props);
	}

	private ActorRef getTestCustomerService(ActorRef accountService, Map<Long, ActorRef> accountsById) {
		Props props = CustomerService.props(accountService, new TestCustomerFactory(accountsById),
				Duration.ofSeconds(1));
		return system.actorOf(props);
	}

	static class TestCustomerFactory extends CustomerFactory {
		private Map<Long, ActorRef> customersById;

		TestCustomerFactory(Map<Long, ActorRef> customersById) {
			this.customersById = customersById;
		}

		@Override
		public ActorRef get(ActorContext context, Customer customer) {
			return customersById.get(customer.getId());
		}
	}

	@Test
	void testCreateCustomerReturnsSuccessIfAccountDoesNotExist() {
		new TestKit(system) {
			{
				ActorRef customerService = getTestCustomerService(getTestAccountService(false), Collections.emptyMap());
				Customer expectedCustomer = Customer.builder().id(1L).name("TEST_CUSTOMER").accountNumber(1L).build();
				customerService.tell(expectedCustomer, getRef());
				expectMsgClass(Success.class);
			}
		};
	}

	@Test
	void testCreateCustomerReturnsFailureIfNoResponseAccountService() {
		new TestKit(system) {
			{
				Customer expectedCustomer = Customer.builder().id(1L).name("TEST_CUSTOMER").accountNumber(1L).build();
				ActorRef customerActor = getTestCustomer(expectedCustomer);
				ActorRef customerService = getTestCustomerService(getTestAccountService(true),
						Collections.singletonMap(1L, customerActor));
				customerService.tell(expectedCustomer, getRef());
				expectMsgClass(Failure.class);
				customerService.tell(expectedCustomer, getRef());
				expectMsg(new Failure("Customer Account creation failed"));
			}
		};
	}

	@Test
	void testGetCustomerReturnsExistingCustomer() {
		new TestKit(system) {
			{
				Customer expectedCustomer = Customer.builder().id(1L).name("TEST_CUSTOMER").accountNumber(1L).build();
				ActorRef customerActor = getTestCustomer(expectedCustomer);
				ActorRef customerService = getTestCustomerService(getTestAccountService(false),
						Collections.singletonMap(1L, customerActor));
				customerService.tell(expectedCustomer, getRef());
				expectMsgClass(Success.class);
				customerService.tell(new GetCustomer(1L), getRef());
				expectMsg(expectedCustomer);
			}
		};
	}

	@Test
	void testGetCustomerReturnsFailureIfCustomerNotExisting() {
		new TestKit(system) {
			{
				ActorRef customerService = getTestCustomerService(getTestAccountService(true), Collections.emptyMap());
				customerService.tell(new GetCustomer(1L), getRef());
				expectMsg(new Failure("Customer " + 1L + " not found"));
			}
		};
	}

	@Test
	void testDeleteCustomerReturnsSuccessIfCustomerExists() {
		new TestKit(system) {
			{
				Customer expectedCustomer = Customer.builder().id(1L).name("TEST_CUSTOMER").accountNumber(1L).build();
				ActorRef customerActor = getTestCustomer(expectedCustomer);
				ActorRef customerService = getTestCustomerService(getTestAccountService(false),
						Collections.singletonMap(1L, customerActor));
				customerService.tell(expectedCustomer, getRef());
				expectMsgClass(Success.class);
				customerService.tell(new DeleteCustomer(1L), getRef());
				expectMsgClass(Success.class);
			}
		};
	}

	@Test
	void testDeleteCustomerReturnsFailureIfCustomerNotFound() {
		new TestKit(system) {
			{
				ActorRef customerService = getTestCustomerService(getTestAccountService(true), Collections.emptyMap());
				customerService.tell(new DeleteCustomer(1L), getRef());
				expectMsg(new Failure("Customer " + 1L + " not found"));
			}
		};
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
			return receiveBuilder().match(Customer.class, x -> {
				if (failOnTransfer) {
					sender().tell(new Failure(x.toString()), self());
				} else {
					sender().tell(new Success(), self());
				}
			}).build();

		}
	}

}
