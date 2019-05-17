package com.rev.money.transfer.model;

import java.io.Serializable;

import lombok.Data;

public class MessageStatus {

	private MessageStatus() {
	}

	@Data
	public static class Success implements Serializable {
		/**
		* 
		*/
		private static final long serialVersionUID = -7882059500166242534L;
	}

	@Data
	public static class Failure implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1578078649679879719L;
		private final String message;
	}
}
