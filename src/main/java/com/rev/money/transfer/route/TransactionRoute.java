package com.rev.money.transfer.route;

import static akka.http.javadsl.server.PathMatchers.separateOnSlashes;
import static akka.pattern.Patterns.ask;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.API.Match.Pattern0.any;
import static io.vavr.Predicates.instanceOf;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

import com.rev.money.transfer.actor.TransactionActor;
import com.rev.money.transfer.dto.CashDepositDTO;
import com.rev.money.transfer.dto.TransactionDTO;
import com.rev.money.transfer.mapper.CashDepositMapper;
import com.rev.money.transfer.mapper.TransactionMapper;
import com.rev.money.transfer.model.MessageStatus.Failure;
import com.rev.money.transfer.model.Transaction;
import com.rev.money.transfer.service.TransactionService.TransactionRolledBack;
import com.rev.money.transfer.util.Constant;

import akka.actor.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionRoute extends AllDirectives {

	private final ActorRef transactionService;
	private final Duration timeout;

	/**
	 * Registers the routes with the mappings between URLs and actions to be
	 * performed for each patterns
	 * 
	 * @return {@link Route}
	 */
	public Route routes() {
		return pathPrefix(Constant.TRANSACTION_ROUTE_PATH,
				() -> route(postTransaction(),
						path(separateOnSlashes(Constant.DEPOSIT_ROUTE_PATH), () -> depositAmount()),
						path(PathMatchers.longSegment(), id -> route(getTransaction(id)))));
	}

	/************************************************************************************************************
	 * 						Methods for transfer the amount from one account to another 						*
	 ************************************************************************************************************/

	private Route postTransaction() {
		return pathEnd(() -> post(() -> entity(Jackson.unmarshaller(TransactionDTO.class), this::transfer)));
	}

	private Route transfer(TransactionDTO transactionDto) {
		Long id = transactionDto.getId();
		if (id == null || id <= 0) {
			return complete(StatusCodes.BAD_REQUEST, "Id can not be null or less than zero");
		}
		BigDecimal amount = transactionDto.getAmount();
		if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
			return complete(StatusCodes.BAD_REQUEST, "Amount can not be null or less than zero");
		}
		CompletionStage<Object> transferResponse = ask(transactionService,
				new TransactionMapper.TransactionDtoToModelMapper().apply(transactionDto), timeout);
		return onSuccess(transferResponse, this::handleTransferResponse);
	}

	private Route handleTransferResponse(Object transferResponse) {
		return Match(transferResponse).of(
				Case($(instanceOf(Failure.class)),
						failure -> complete(StatusCodes.BAD_REQUEST, failure, Jackson.marshaller())),
				Case($(instanceOf(Transaction.class)),
						transaction -> complete(
								StatusCodes.CREATED, new TransactionMapper.TransactionToDtoMapper().apply(transaction),
								Jackson.marshaller())),
				Case($(instanceOf(TransactionRolledBack.class)),
						rollback -> complete(StatusCodes.CREATED,
								new TransactionMapper.TransactionToDtoMapper().apply(rollback.getTransactionInfo()),
								Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR))

		);
	}

	/************************************************************************************************************
	 * 									Methods for retrieving the Transaction 									*
	 ************************************************************************************************************/

	private Route getTransaction(long id) {
		return get(() -> {
			CompletionStage<Object> getTransactionResponse = ask(transactionService,
					new TransactionActor.GetTransaction(id), timeout);
			return onSuccess(() -> getTransactionResponse, this::handleGetTransactionResponse);
		});
	}

	private Route handleGetTransactionResponse(Object response) {
		return Match(response).of(
				Case($(instanceOf(Failure.class)),
						failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
				Case($(instanceOf(Transaction.class)),
						transaction -> complete(StatusCodes.OK, transaction, Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR)));
	}
	
	/************************************************************************************************************
	 * 									Methods for depositing the amount 										*
	 ************************************************************************************************************/
	
	private Route depositAmount() {
		return pathEnd(() -> post(() -> entity(Jackson.unmarshaller(CashDepositDTO.class), this::deposit)));
	}

	private Route deposit(CashDepositDTO cashDeposit) {
		Long id = cashDeposit.getId();
		if (id == null || id <= 0) {
			return complete(StatusCodes.BAD_REQUEST, "Amount can not be null or less than zero");
		}
		BigDecimal amount = cashDeposit.getAmount();
		if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
			return complete(StatusCodes.BAD_REQUEST, "Amount can not be null or less than zero");
		}
		CompletionStage<Object> transferResponse = ask(transactionService,
				new CashDepositMapper.CashDepositDtoToModelMapper().apply(cashDeposit), timeout);
		return onSuccess(transferResponse, this::handleTransferResponse);
	}
	
}
