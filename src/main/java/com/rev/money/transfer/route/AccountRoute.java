package com.rev.money.transfer.route;

import static akka.pattern.Patterns.ask;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.API.Match.Pattern0.any;
import static io.vavr.Predicates.instanceOf;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import com.rev.money.transfer.actor.AccountActor;
import com.rev.money.transfer.dto.AccountDTO;
import com.rev.money.transfer.mapper.AccountMapper;
import com.rev.money.transfer.model.Account;
import com.rev.money.transfer.model.MessageStatus;
import com.rev.money.transfer.util.Constant;

import akka.actor.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountRoute extends AllDirectives {

	private final ActorRef accountService;
	private final Duration timeout;

	/**
	 * Registers the routes with the mappings between URLs and actions to be performed for each patterns
	 * 
	 * @return {@link Route}
	 */
	public Route routes() {
		return pathPrefix(Constant.ACCOUNT_ROUTE_PATH, () -> route(createAccount(), path(PathMatchers.longSegment(),
				accountNumber -> route(getAccount(accountNumber), deleteAccount(accountNumber)))));
	}

	/************************************************************************************************************
	 * 								      Methods for creating the Account 										*
	 ************************************************************************************************************/

	private Route createAccount() {
		return pathEnd(() -> post(() -> entity(Jackson.unmarshaller(AccountDTO.class), accountDto -> {
			CompletionStage<Object> createAccountResponse = ask(accountService,
					new AccountMapper.AccountDtoToModelMapper().apply(accountDto), timeout);
			return onSuccess(createAccountResponse, this::handleCreateAccountResponse);
		})));
	}

	private Route handleCreateAccountResponse(Object createAccountResponse) {
		return Match(createAccountResponse).of(
				Case($(instanceOf(MessageStatus.Success.class)), success -> complete(StatusCodes.CREATED)),
				Case($(instanceOf(MessageStatus.Failure.class)),
						failure -> complete(StatusCodes.BAD_REQUEST, failure, Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR)));
	}

	/************************************************************************************************************
	 * 								     Methods for retrieving the Account 								    *
	 ************************************************************************************************************/

	private Route getAccount(Long accountNumber) {
		return get(() -> {
			CompletionStage<Object> getAccountResponse = ask(accountService, new AccountActor.GetAccount(accountNumber),
					timeout);
			return onSuccess(() -> getAccountResponse, this::handleGetAccountResponse);
		});
	}

	private Route handleGetAccountResponse(Object getCustomerResponse) {
		return Match(getCustomerResponse).of(
				Case($(instanceOf(Account.class)),
						account -> complete(StatusCodes.OK, new AccountMapper.AccountToDtoMapper().apply(account),
								Jackson.marshaller())),
				Case($(instanceOf(com.rev.money.transfer.model.MessageStatus.Failure.class)),
						failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR)));
	}

	/************************************************************************************************************
	 * 							Methods for deleting/deactivating the Account 								    *
	 ************************************************************************************************************/

	private Route deleteAccount(Long accountNumber) {
		return delete(() -> {
			CompletionStage<Object> deleteAccountResponse = ask(accountService, new AccountActor.DeleteAccount(accountNumber),
					timeout);
			return onSuccess(() -> deleteAccountResponse, this::handleDeleteAccountResponse);
		});
	}

	private Route handleDeleteAccountResponse(Object deleteAccountResponse) {
		return Match(deleteAccountResponse).of(
				Case($(instanceOf(MessageStatus.Success.class)), success -> complete(StatusCodes.OK)),
				Case($(instanceOf(MessageStatus.Failure.class)),
						failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR)));
	}

}
