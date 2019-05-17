package com.rev.money.transfer.service;

import static akka.pattern.Patterns.ask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.rev.money.transfer.actor.CustomerActor;
import com.rev.money.transfer.factory.CustomerFactory;
import com.rev.money.transfer.model.Customer;
import com.rev.money.transfer.model.MessageStatus;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.MessageStatus.Success;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;

public class CustomerService extends AbstractLoggingActor {

	private final CustomerFactory customerFactory;
	// In-memory store for CRUD operations
	private final Map<Long, ActorRef> customersById = new HashMap<>();
	private final ActorRef accountService;
	private final Duration timeout;
	private final AtomicLong sequence;

	private CustomerService(ActorRef accountService, CustomerFactory customerFactory, Duration timeout) {
		this.accountService = accountService;
		this.customerFactory = customerFactory;
		this.timeout = timeout;
		this.sequence = new AtomicLong(0);
	}

	public static Props props(ActorRef accountService, CustomerFactory customerFactory, Duration timeout) {
		return Props.create(CustomerService.class, () -> new CustomerService(accountService, customerFactory, timeout));
	}

	/**
	 * Mail box to receive the messages
	 */
	@Override
	public Receive createReceive() {
		return receiveBuilder().match(Customer.class, this::onCustomer)
				.match(CustomerActor.GetCustomer.class, this::onGetCustomer)
				.match(CustomerActor.DeleteCustomer.class, this::onDeleteCustomer).build();
	}

	/**
	 * For the messages of type {@link Customer}
	 * 
	 * @param customer
	 */
	private void onCustomer(Customer customer) {
		long id = sequence.incrementAndGet();
		log().debug("Sequence for Customer and Account Id: {}", sequence);
		if (customersById.containsKey(id)) {
			replyCustomerAlreadyExists(id);
			return;
		}
		createCustomer(customer);
	}

	/**
	 * Error messages for existing customer
	 * 
	 * @param accountNumber
	 */
	private void replyCustomerAlreadyExists(long id) {
		String errorMsg = "Customer " + id + " already exists";
		log().info(errorMsg);
		sender().tell(new MessageStatus.Failure(errorMsg), self());
	}

	private void createCustomer(Customer customer) {
		long id = sequence.longValue();
		customer = Customer.builder().id(id).name(customer.getName()).accountNumber(id)
				.contactNumber(customer.getContactNumber()).email(customer.getEmail()).zipCode(customer.getZipCode())
				.build();
		ActorRef actorRef = customerFactory.get(context(), customer);
		customersById.put(id, actorRef);
		log().info("Customer {} created", id);
		createAccount(customer);
	}

	private void createAccount(Customer customer) {
		ActorRef replyTo = sender();
		log().debug("In progress of creating Account for new customer");
		ask(accountService, customer, timeout)
				.thenAcceptAsync(accountCreationResponse -> handleCreateAccountResponse(customer.getId(),
						accountCreationResponse, replyTo));
	}

	private void handleCreateAccountResponse(Long id, Object accountCreationResponse, ActorRef replyTo) {
		if (accountCreationResponse instanceof Failure) {
			customersById.remove(id);
			sequence.decrementAndGet();
			String errorMsg = "Customer Account creation failed";
			log().warning(errorMsg);
			replyTo.tell(new Failure(errorMsg), replyTo);
		} else {
			log().info("Customer Account created successfully: CustomerId: {}", id);
			replyTo.tell(new Success(), replyTo);
		}
	}

	/**
	 * Get the customer by customerId
	 * 
	 * @param getCustomer
	 */
	private void onGetCustomer(CustomerActor.GetCustomer getCustomer) {
		long id = getCustomer.getId();
		ActorRef customer = customersById.get(id);
		if (customer == null) {
			replyCustomerNotFound(id);
			return;
		}
		forwardGetCustomer(customer, getCustomer);
	}

	private void forwardGetCustomer(ActorRef customer, CustomerActor.GetCustomer getCustomer) {
		ActorRef replyTo = sender();
		ask(customer, getCustomer, timeout).thenAcceptAsync(c -> replyTo.tell(c, self()));
	}

	private void replyCustomerNotFound(long id) {
		String errorMsg = "Customer " + id + " not found";
		log().warning(errorMsg);
		sender().tell(new MessageStatus.Failure(errorMsg), self());
	}

	/**
	 * Deactivate/Delete the customer
	 * 
	 * @param deleteCustomer
	 */
	private void onDeleteCustomer(CustomerActor.DeleteCustomer deleteCustomer) {
		long id = deleteCustomer.getId();
		if (!customersById.containsKey(id)) {
			replyCustomerNotFound(id);
			return;
		}
		customersById.remove(id);
		sender().tell(new MessageStatus.Success(), self());
	}

}
