package com.rev.money.transfer.route;

import static akka.pattern.Patterns.ask;
import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.API.Match.Pattern0.any;
import static io.vavr.Predicates.instanceOf;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import com.rev.money.transfer.actor.CustomerActor;
import com.rev.money.transfer.dto.CustomerDTO;
import com.rev.money.transfer.mapper.CustomerMapper;
import com.rev.money.transfer.model.Customer;
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
public class CustomerRoute extends AllDirectives {

	private final ActorRef customerService;
	private final Duration timeout;

	/**
	 * Registers the routes with the mappings between URLs and actions to be
	 * performed for each patterns
	 * 
	 * @return {@link Route}
	 */
	public Route routes() {
		return pathPrefix(Constant.CUSTOMER_ROUTE_PATH, () -> route(createCustomer(),
				path(PathMatchers.longSegment(), id -> route(getCustomer(id), deleteCustomer(id)))));
	}
	
	/************************************************************************************************************
	 * 								      Methods for creating the Customer 									*
	 ************************************************************************************************************/

	private Route createCustomer() {
		return pathEnd(() -> post(() -> entity(Jackson.unmarshaller(CustomerDTO.class), customerDto -> {
			customerDto.setAccountNumber(customerDto.getId());
			CompletionStage<Object> createCustomerResponse = ask(customerService,
					new CustomerMapper.CustomerDtoToModelMapper().apply(customerDto), timeout);
			return onSuccess(createCustomerResponse, this::handleCreateCustomerResponse);
		})));
	}

	private Route handleCreateCustomerResponse(Object createCustomerResponse) {
		return Match(createCustomerResponse).of(
				Case($(instanceOf(MessageStatus.Success.class)), success -> complete(StatusCodes.CREATED)),
				Case($(instanceOf(MessageStatus.Failure.class)),
						failure -> complete(StatusCodes.BAD_REQUEST, failure, Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR)));
	}
	
	/************************************************************************************************************
	 * 								     Methods for retrieving the Customer 								    *
	 ************************************************************************************************************/
	
	private Route getCustomer(Long id) {
		return get(() -> {
			CompletionStage<Object> getCustomerResponse = ask(customerService, new CustomerActor.GetCustomer(id),
					timeout);
			return onSuccess(() -> getCustomerResponse, this::handleGetCustomerResponse);
		});
	}
	
	private Route handleGetCustomerResponse(Object getCustomerResponse) {
		return Match(getCustomerResponse).of(
				Case($(instanceOf(Customer.class)),
						customer -> complete(StatusCodes.OK, new CustomerMapper.CustomerToDtoMapper().apply(customer),
								Jackson.marshaller())),
				Case($(instanceOf(com.rev.money.transfer.model.MessageStatus.Failure.class)),
						failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR)));
	}
	
	/************************************************************************************************************
	 * 							Methods for deleting/deactivating the Customer 								    *
	 ************************************************************************************************************/
	
	private Route deleteCustomer(Long id) {
		return delete(() -> {
			CompletionStage<Object> deleteCustomerResponse = ask(customerService, new CustomerActor.DeleteCustomer(id),
					timeout);
			return onSuccess(() -> deleteCustomerResponse, this::handleDeleteCustomerResponse);
		});
	}

	private Route handleDeleteCustomerResponse(Object deleteCustomerResponse) {
		return Match(deleteCustomerResponse).of(
				Case($(instanceOf(MessageStatus.Success.class)), success -> complete(StatusCodes.OK)),
				Case($(instanceOf(MessageStatus.Failure.class)),
						failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
				Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR)));
	}

}
