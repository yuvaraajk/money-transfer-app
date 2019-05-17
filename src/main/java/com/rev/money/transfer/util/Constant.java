package com.rev.money.transfer.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Constant {

	public static final String DEFAULT_SERVER_ADDR = "localhost:8080";
	public static final String SERVER_ADDR = "server.address";
	public static final String ACTOR_TIMEOUT = "actor.timeout";
	public static final String DEFAULT_TIME_OUT_DURATION = "1";
	public static final String SYSTEM_NAME = "system.name";

	public static final String CUSTOMER_SERVICE = "customerService";
	public static final String ACCOUNT_SERVICE = "accountService";
	public static final String TRANSACTION_SERVICE = "transactionService";

	public static final String CUSTOMER_ROUTE_PATH = "customers";
	public static final String ACCOUNT_ROUTE_PATH = "accounts";
	public static final String TRANSACTION_ROUTE_PATH = "transactions";
	public static final String DEPOSIT_ROUTE_PATH = "deposit";
	public static final String WITHDRAW_ROUTE_PATH = "deposit";

}
