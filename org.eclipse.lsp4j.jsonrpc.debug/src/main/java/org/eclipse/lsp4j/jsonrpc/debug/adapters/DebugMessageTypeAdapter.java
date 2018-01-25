/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.lsp4j.jsonrpc.debug.adapters;

import java.io.IOException;

import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugNotificationMessage;
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage;
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.MethodProvider;
import org.eclipse.lsp4j.jsonrpc.json.adapters.MessageTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * The {@link DebugMessageTypeAdapter} provides an adapter that maps Debug
 * Server Protocol style JSON to/from LSP4J's JSONRPC implementation. The Debug
 * Server Protocol (DSP) has its own message format that is quite similar to
 * JSON-RPC 2.0. The DSP is defined in a <a href=
 * "https://github.com/Microsoft/vscode-debugadapter-node/blob/master/debugProtocol.json">JSON
 * schema in the VS Code Debug Adapter</a>. This section documents how LSP4J's
 * jsonrpc classes maps to the Debug Protocol, using some extensions in the DSP
 * code to the lsp4j's {@link Message}s.
 * <p>
 *
 * <pre>
	"ProtocolMessage": { // implemented by {@link Message}
		"type": "object",
		"description": "Base class of requests, responses, and events.",
		"properties": {
			"seq": { // implemented by (depending on type, with conversion to/from String):
			         //  {@link DebugRequestMessage#getId()}, or
			         //  {@link DebugNotificationMessage#getId()} or
			         //  {@link DebugResponseMessage#getResponseId()}
				"type": "integer",
				"description": "Sequence number."
			},
			"type": { // implicit in type of subclass of {@link Message}
				"type": "string",
				"description": "Message type.",
				"_enum": [ "request", "response", "event" ]
			}
		},
		"required": [ "seq", "type" ]
	},

	"Request": { // implemented by {@link DebugRequestMessage}
		"allOf": [ { "$ref": "#/definitions/ProtocolMessage" }, {
			"type": "object",
			"description": "A client or server-initiated request.",
			"properties": {
				"type": { // implicit by being of type {@link DebugRequestMessage}
					"type": "string",
					"enum": [ "request" ]
				},
				"command": { // implemented by {@link DebugRequestMessage#getMethod()}
					"type": "string",
					"description": "The command to execute."
				},
				"arguments": { // implemented by {@link DebugRequestMessage#getParams()}
					"type": [ "array", "boolean", "integer", "null", "number" , "object", "string" ],
					"description": "Object containing arguments for the command."
				}
			},
			"required": [ "type", "command" ]
		}]
	},

	"Event": { // implemented by {@link DebugNotificationMessage}
		"allOf": [ { "$ref": "#/definitions/ProtocolMessage" }, {
			"type": "object",
			"description": "Server-initiated event.",
			"properties": {
				"type": { // implicit by being of type {@link DebugNotificationMessage}
					"type": "string",
					"enum": [ "event" ]
				},
				"event": { // implemented by {@link DebugNotificationMessage#getMethod()}
					"type": "string",
					"description": "Type of event."
				},
				"body": { // implemented by {@link DebugNotificationMessage#getParams()}
					"type": [ "array", "boolean", "integer", "null", "number" , "object", "string" ],
					"description": "Event-specific information."
				}
			},
			"required": [ "type", "event" ]
		}]
	},

	"Response": { // implemented by {@link DebugResponseMessage}
		"allOf": [ { "$ref": "#/definitions/ProtocolMessage" }, {
			"type": "object",
			"description": "Response to a request.",
			"properties": {
				"type": { // implicit by being of type {@link DebugResponseMessage}
					"type": "string",
					"enum": [ "response" ]
				},
				"request_seq": { // implemented by {@link DebugResponseMessage#getId()}
					"type": "integer",
					"description": "Sequence number of the corresponding request."
				},
				"success": { // implemented by {@link DebugResponseMessage#getError()} == null
					"type": "boolean",
					"description": "Outcome of the request."
				},
				"command": { // implemented by {@link DebugResponseMessage#getMethod()}
					"type": "string",
					"description": "The command requested."
				},
				"message": { // implemented by {@link ResponseError#getMessage()}
					"type": "string",
					"description": "Contains error message if success == false."
				},
				"body": { // implemented by {@link DebugResponseMessage#getResult()} for success and {@link ResponseError#getData()} for error
					"type": [ "array", "boolean", "integer", "null", "number" , "object", "string" ],
					"description": "Contains request result if success is true and optional error details if success is false."
				}
			},
			"required": [ "type", "request_seq", "success", "command" ]
		}]
	},
 * </pre>
 *
 */
public class DebugMessageTypeAdapter extends MessageTypeAdapter {

	public static class Factory implements TypeAdapterFactory {

		private final MessageJsonHandler handler;

		public Factory(MessageJsonHandler handler) {
			this.handler = handler;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
			if (!Message.class.isAssignableFrom(typeToken.getRawType()))
				return null;
			return (TypeAdapter<T>) new DebugMessageTypeAdapter(handler, gson);
		}

	}

	private final MessageJsonHandler handler;
	private final Gson gson;

	public DebugMessageTypeAdapter(MessageJsonHandler handler, Gson gson) {
		super(handler, gson);
		this.handler = handler;
		this.gson = gson;
	}

	@Override
	public Message read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}

		in.beginObject();
		String messageType = null, id = null, seq = null, request_seq = null, method = null, message = null;
		Boolean rawSuccess = null;
		Object rawParams = null;
		Object rawBody = null;
		while (in.hasNext()) {
			String name = in.nextName();
			switch (name) {
			case "seq": {
				seq = in.nextString();
				break;
			}
			case "request_seq": {
				// on responses we treat the request_seq as the id
				request_seq = in.nextString();
				break;
			}
			case "type": {
				messageType = in.nextString();
				break;
			}
			case "success": {
				rawSuccess = in.nextBoolean();
				break;
			}
			case "command": {
				method = in.nextString();
				break;
			}
			case "event": {
				method = in.nextString();
				break;
			}
			case "message": {
				if (in.peek() == JsonToken.NULL) {
					in.nextNull();
				} else {
					message = in.nextString();
				}
				break;
			}
			case "arguments": {
				rawParams = parseParams(in, method);
				break;
			}
			case "body": {
				rawBody = parseBody(in, messageType, request_seq, method, rawSuccess);
				break;
			}
			default:
				in.skipValue();
			}
		}
		id = request_seq != null ? request_seq : seq;
		in.endObject();
		boolean success = rawSuccess != null ? rawSuccess : true;
		Object params = parseParams(rawParams, method);
		Object body = parseBody(rawBody, messageType, request_seq, method, success);
		return createMessage(messageType, id, method, success, message, params, body);
	}

	/**
	 * Convert the json input into the body object corresponding to the type of
	 * message.
	 *
	 * If the type of message or any other necessary field is not known until after
	 * parsing, call {@link #parseBody(Object, String, String, String, Boolean)} on
	 * the return value of this call for a second chance conversion.
	 *
	 * @param in
	 *            json input to read from
	 * @param messageType
	 *            message type if known
	 * @param request_seq
	 *            seq id of request message if known
	 * @param method
	 *            event/method being called
	 * @param success
	 *            if success of a response is known
	 * @return correctly typed object if the correct expected type can be
	 *         determined, or a JsonElement representing the body
	 */
	protected Object parseBody(JsonReader in, String messageType, String request_seq, String method, Boolean success)
			throws IOException {
		if ("event".equals(messageType)) {
			return parseParams(in, method);
		} else if ("response".equals(messageType) && success != null && success) {
			return super.parseResult(in, request_seq);
		} else {
			return new JsonParser().parse(in);
		}
	}

	/**
	 * Convert the JsonElement into the body object corresponding to the type of
	 * message. If the rawBody is already converted, does nothing.
	 *
	 * @param rawBody
	 *            json element to read from
	 * @param messageType
	 *            message type if known
	 * @param request_seq
	 *            seq id of request message if known
	 * @param method
	 *            event/method being called
	 * @param success
	 *            if success of a response is known
	 * @return correctly typed object if the correct expected type can be
	 *         determined, or rawBody unmodified if no conversion can be done.
	 */
	protected Object parseBody(Object rawBody, String messageType, String request_seq, String method, Boolean success) {
		if ("event".equals(messageType)) {
			return parseParams(rawBody, method);
		} else if ("response".equals(messageType)) {
			if (success != null && success) {
				return super.parseResult(rawBody, request_seq);
			}
			if (isNull(rawBody)) {
				return null;
			}
			if (!(rawBody instanceof JsonElement)) {
				return rawBody;
			}
			JsonElement rawJsonParams = (JsonElement) rawBody;
			return fromJson(rawJsonParams, Object.class);
		}
		return rawBody;
	}

	private Message createMessage(String messageType, String id, String method, boolean success, String errorMessage,
			Object params, Object body) {
		if (messageType == null) {
			throw new JsonParseException("Unable to identify the input message. Missing 'type' field.");
		}
		switch (messageType) {
		case "request": {
			RequestMessage message = new RequestMessage();
			message.setId(id);
			message.setMethod(method);
			message.setParams(params);
			return message;
		}
		case "event": {
			DebugNotificationMessage message = new DebugNotificationMessage();
			message.setId(id);
			message.setMethod(method);
			message.setParams(body);
			return message;
		}
		case "response": {
			DebugResponseMessage message = new DebugResponseMessage();
			message.setId(id);
			if (!success) {
				ResponseError error = new ResponseError();
				error.setCode(ResponseErrorCode.UnknownErrorCode);
				error.setData(body);
				error.setMessage(errorMessage);
				message.setError(error);
			} else {
				if (body instanceof JsonElement) {
					// Type of result could not be resolved - try again with the parsed JSON tree
					MethodProvider methodProvider = handler.getMethodProvider();
					if (methodProvider != null) {
						String resolvedMethod = methodProvider.resolveMethod(id);
						if (resolvedMethod != null) {
							JsonRpcMethod jsonRpcMethod = handler.getJsonRpcMethod(resolvedMethod);
							if (jsonRpcMethod != null)
								body = gson.fromJson((JsonElement) body, jsonRpcMethod.getReturnType());
						}
					}
				}
				message.setResult(body);
			}
			return message;
		}
		default:
			throw new JsonParseException("Unable to identify the input message.");
		}
	}

	@Override
	public void write(JsonWriter out, Message message) throws IOException {
		out.beginObject();
		if (message instanceof DebugRequestMessage) {
			DebugRequestMessage requestMessage = (DebugRequestMessage) message;
			out.name("type");
			out.value("request");
			out.name("seq");
			out.value(Integer.parseInt(requestMessage.getId()));
			out.name("command");
			out.value(requestMessage.getMethod());
			out.name("arguments");
			Object params = requestMessage.getParams();
			if (params == null)
				writeNullValue(out);
			else
				gson.toJson(params, params.getClass(), out);
		} else if (message instanceof DebugResponseMessage) {
			DebugResponseMessage responseMessage = (DebugResponseMessage) message;
			out.name("type");
			out.value("response");
			out.name("seq");
			out.value(Integer.parseInt(responseMessage.getResponseId()));
			out.name("request_seq");
			out.value(Integer.parseInt(responseMessage.getId()));
			out.name("command");
			out.value(responseMessage.getMethod());
			ResponseError error = responseMessage.getError();
			if (error != null) {
				out.name("success");
				out.value(false);
				String errorMessage = error.getMessage();
				out.name("message");
				if (errorMessage == null)
					writeNullValue(out);
				else
					gson.toJson(errorMessage, errorMessage.getClass(), out);

				Object errorData = error.getData();
				if (errorData != null) {
					out.name("body");
					gson.toJson(errorData, errorData.getClass(), out);
				}
			} else {
				out.name("body");
				Object result = responseMessage.getResult();
				if (result == null)
					writeNullValue(out);
				else
					gson.toJson(result, result.getClass(), out);
			}
		} else if (message instanceof DebugNotificationMessage) {
			DebugNotificationMessage notificationMessage = (DebugNotificationMessage) message;
			out.name("type");
			out.value("event");
			out.name("seq");
			if (notificationMessage.getId() != null) {
				out.value(Integer.parseInt(notificationMessage.getId()));
			} else {
				out.value(0);
			}
			out.name("command");
			out.value(notificationMessage.getMethod());
			out.name("body");
			Object params = notificationMessage.getParams();
			if (params == null)
				writeNullValue(out);
			else
				gson.toJson(params, params.getClass(), out);
		}

		out.endObject();
	}
}
