package com.rev.money.transfer.actor;

import java.io.Serializable;

import com.rev.money.transfer.model.Customer;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import lombok.Data;

public class CustomerActor extends AbstractLoggingActor {

	private Customer customer;

	private CustomerActor(Customer customer) {
		this.customer = customer;
	}

	public static Props props(Customer customer) {
		return Props.create(CustomerActor.class, () -> new CustomerActor(customer));
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(GetCustomer.class, this::onGetCustomer)
				.match(DeleteCustomer.class, this::onDeleteCustomer).build();
	}

	@Data
	public static class DeleteCustomer implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -5801935749707561324L;
		private final long id;
	}

	private void onDeleteCustomer(DeleteCustomer deleteCustomer) {
		sender().tell(customer, getSelf());
	}

	@Data
	public static class GetCustomer implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 5865604696984327919L;
		private final long id;
	}

	private void onGetCustomer(GetCustomer getCustomer) {
		sender().tell(customer, self());
	}

}
