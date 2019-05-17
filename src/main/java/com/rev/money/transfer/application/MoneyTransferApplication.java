package com.rev.money.transfer.application;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

import com.rev.money.transfer.factory.AccountFactory;
import com.rev.money.transfer.factory.CustomerFactory;
import com.rev.money.transfer.factory.TransactionFactory;
import com.rev.money.transfer.route.AccountRoute;
import com.rev.money.transfer.route.CustomerRoute;
import com.rev.money.transfer.route.TransactionRoute;
import com.rev.money.transfer.service.AccountService;
import com.rev.money.transfer.service.CustomerService;
import com.rev.money.transfer.service.TransactionService;
import com.rev.money.transfer.util.Constant;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;

public class MoneyTransferApplication extends AllDirectives {

	private final LoggingAdapter log;
	private final ActorRef customerService;
	private final ActorRef accountService;
	private final ActorRef transactionService;
	private final ActorSystem system = ActorSystem.create("money-transfer");
	private final String address;
	private final Duration timeout;

	public MoneyTransferApplication(CustomerFactory customerFactory, AccountFactory accountFactory,
			TransactionFactory transactionFactory, String address, Duration timeout) {
		accountService = system.actorOf(AccountService.props(accountFactory, timeout), Constant.ACCOUNT_SERVICE);
		customerService = system.actorOf(CustomerService.props(accountService, customerFactory, timeout), Constant.CUSTOMER_SERVICE);
		transactionService = system.actorOf(TransactionService.props(accountService, transactionFactory, timeout),
				Constant.TRANSACTION_SERVICE);
		this.timeout = timeout;
		this.address = address;
		this.log = Logging.getLogger(system, this);
	}

	public static void main(String[] args) throws IOException {
		Config conf = ConfigFactory.load();
		String address = getPropertyValue(conf, Constant.SERVER_ADDR, Constant.DEFAULT_SERVER_ADDR);
		Duration timeout = Duration
				.parse(getPropertyValue(conf, Constant.ACTOR_TIMEOUT, Constant.DEFAULT_TIME_OUT_DURATION));
		MoneyTransferApplication application = new MoneyTransferApplication(new CustomerFactory(), new AccountFactory(),
				new TransactionFactory(), address, timeout);
		CompletionStage<ServerBinding> binding = application.createServerBinding();
		application.log.info("Server online at {}\nPress RETURN to stop...", application.address);
		System.in.read();
		binding.thenCompose(ServerBinding::unbind).thenAccept(unbound -> application.system.terminate());
	}

	private CompletionStage<ServerBinding> createServerBinding() {
		ActorMaterializer materializer = ActorMaterializer.create(system);
		Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = buildRoutes().flow(system, materializer);
		Http http = Http.get(system);
		return http.bindAndHandle(routeFlow, ConnectHttp.toHost(address), materializer);
	}

	public Route buildRoutes() {
		return route(new CustomerRoute(customerService, timeout).routes(),
				new AccountRoute(accountService, timeout).routes(),
				new TransactionRoute(transactionService, timeout).routes());
	}

	private static String getPropertyValue(Config conf, String property, String defaultValue) {
		return conf.hasPath(property) ? conf.getString(property) : defaultValue;
	}

	public ActorRef getAccountService() {
		return accountService;
	}

	public ActorRef getTransactionService() {
		return transactionService;
	}

}
