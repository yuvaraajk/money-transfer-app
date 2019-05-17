package com.rev.money.transfer.factory;

import com.rev.money.transfer.actor.TransactionActor;
import com.rev.money.transfer.model.Transaction;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

public class TransactionFactory {

	public ActorRef get(ActorContext context, Transaction transaction) {
        return context.actorOf(TransactionActor.props(transaction));
    }

}
