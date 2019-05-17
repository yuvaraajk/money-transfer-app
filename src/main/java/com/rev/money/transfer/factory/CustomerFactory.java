package com.rev.money.transfer.factory;

import com.rev.money.transfer.actor.CustomerActor;
import com.rev.money.transfer.model.Customer;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

public class CustomerFactory {

	public ActorRef get(ActorContext context, Customer customer) {
		return context.actorOf(CustomerActor.props(customer), "customer_" + customer.getId());
	}

}
