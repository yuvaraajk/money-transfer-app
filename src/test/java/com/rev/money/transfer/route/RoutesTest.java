package com.rev.money.transfer.route;

import static akka.pattern.Patterns.ask;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.junit.Before;

import com.rev.money.transfer.application.MoneyTransferApplication;
import com.rev.money.transfer.dto.AccountDTO;
import com.rev.money.transfer.dto.TransactionDTO;
import com.rev.money.transfer.factory.AccountFactory;
import com.rev.money.transfer.factory.CustomerFactory;
import com.rev.money.transfer.factory.TransactionFactory;
import com.rev.money.transfer.model.Account;
import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.model.TransactionStatus;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;

public class RoutesTest extends JUnitRouteTest {

	private TestRoute appRoute;
	private MoneyTransferApplication app;
	private Duration timeout = Duration.ofSeconds(1);

	@Before
	public void before() {
		app = new MoneyTransferApplication(new CustomerFactory(), new AccountFactory(), new TransactionFactory(),
				"localhost:8080", Duration.ofSeconds(1));
		appRoute = testRoute(app.buildRoutes());
	}

	@org.junit.Test
	public void testGetNonExistingAccountReturnsNotFound() {
		appRoute.run(HttpRequest.GET("/accounts/1")).assertStatusCode(StatusCodes.NOT_FOUND);
	}

	@org.junit.Test
	public void testGetExistingAccountReturnsIt() throws InterruptedException, ExecutionException {
		ask(app.getAccountService(), new Account(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
		appRoute.run(HttpRequest.GET("/accounts/1")).assertStatusCode(StatusCodes.OK)
				.assertEntityAs(Jackson.unmarshaller(AccountDTO.class), new AccountDTO(1L, BigDecimal.ZERO));
	}

	@org.junit.Test
	public void testPostNonExistingAccountReturnsOK() {
		appRoute.run(HttpRequest.POST("/accounts").withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
				"{\"accountNumber\": 1, \"balance\": 0}")).assertStatusCode(StatusCodes.CREATED);
	}

	@org.junit.Test
	public void testPostExistingAccountReturnsBadRequest() throws ExecutionException, InterruptedException {
		ask(app.getAccountService(), new Account(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
		appRoute.run(HttpRequest.POST("/accounts").withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
				"{\"accountNumber\": 1, \"balance\": 0}")).assertStatusCode(StatusCodes.BAD_REQUEST);
	}

	@org.junit.Test
	public void testDeleteExistingAccountReturnsOK() throws ExecutionException, InterruptedException {
		ask(app.getAccountService(), new Account(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
		appRoute.run(HttpRequest.DELETE("/accounts/1")).assertStatusCode(StatusCodes.OK);
	}

	@org.junit.Test
	public void testDeleteNonExistingAccountReturnsNotFound() {
		appRoute.run(HttpRequest.DELETE("/accounts/1")).assertStatusCode(StatusCodes.NOT_FOUND);
	}

	@org.junit.Test
	public void testGetNonExistingTransactionReturnsNotFound() {
		appRoute.run(HttpRequest.GET("/transactions/1")).assertStatusCode(StatusCodes.NOT_FOUND);
	}

	@org.junit.Test
	public void testGetTransactionNotFound() throws ExecutionException, InterruptedException {
		ask(app.getTransactionService(),
				new Transaction(1L, 1L, 2L, BigDecimal.ZERO, TransactionStatus.NEW, "transfer"), timeout)
						.toCompletableFuture().get();
		appRoute.run(HttpRequest.GET("/transactions/1")).assertStatusCode(StatusCodes.OK)
				.assertContentType(MediaTypes.APPLICATION_JSON.toContentType())
				.assertEntityAs(Jackson.unmarshaller(TransactionDTO.class),
						new TransactionDTO(1L, 1L, 2L, BigDecimal.ZERO, TransactionStatus.FAIL, "transfer"));
	}

	@org.junit.Test
	public void testSuccessfulTransfer() throws ExecutionException, InterruptedException {
		ask(app.getAccountService(), new Account(1L, BigDecimal.ONE), timeout).toCompletableFuture().get();
		ask(app.getAccountService(), new Account(2L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
		appRoute.run(HttpRequest.POST("/transactions").withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
				"{\"id\": 1, \"remitterAccountId\": 1, \"beneficieryAccountId\": 2, \"amount\": 1, \"status\": \"NEW\", \"remarks\": \"transfer\"}"))
				.assertStatusCode(StatusCodes.CREATED).assertEntityAs(Jackson.unmarshaller(TransactionDTO.class),
						new TransactionDTO(1L, 1L, 2L, BigDecimal.ONE, TransactionStatus.SUCCESS, "transfer"));
	}

	@org.junit.Test
	public void testFailureTransfer() throws ExecutionException, InterruptedException {
		ask(app.getAccountService(), new Account(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
		ask(app.getAccountService(), new Account(2L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
		appRoute.run(HttpRequest.POST("/transactions").withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
				"{\"id\": 1, \"remitterAccountId\": 1, \"beneficieryAccountId\": 2, \"amount\": 1, \"status\": \"NEW\", \"remarks\": \"Insufficient balance to withdraw 1 from account Account(accountNumber=1, balance=0)\"}"))
				.assertStatusCode(StatusCodes.CREATED).assertEntityAs(Jackson.unmarshaller(TransactionDTO.class),
						new TransactionDTO(1L, 1L, 2L, BigDecimal.ONE, TransactionStatus.FAIL,
								"Insufficient balance to withdraw 1 from account Account(accountNumber=1, balance=0)"));
	}
}
