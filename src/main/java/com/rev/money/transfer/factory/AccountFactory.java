package com.rev.money.transfer.factory;

import com.rev.money.transfer.actor.AccountActor;
import com.rev.money.transfer.model.Account;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

public class AccountFactory {

	public ActorRef get(ActorContext context, Account account) {
		return context.actorOf(AccountActor.props(account), "account_" + account.getAccountNumber());
	}

}
