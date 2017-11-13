/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.dsp4j.jsonrpc.adapters;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.dsp4j.jsonrpc.messages.DebugNotificationMessage;
import org.eclipse.dsp4j.jsonrpc.messages.DebugRequestMessage;
import org.eclipse.dsp4j.jsonrpc.messages.DebugResponseMessage;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.MethodProvider;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * The {@link DebugMessageTypeAdapterFactory} provides an adapter that maps
 * Debug Server Protocol style JSON to/from LSP4J's JSONRPC implementation. The
 * Debug Server Protocol (DSP) has its own message format that is quite similar
 * to JSON-RPC 2.0. The DSP is defined in a <a href=
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
public class DebugMessageTypeAdapterFactory implements TypeAdapterFactory {

	private static Type[] EMPTY_TYPE_ARRAY = {};

	private final MessageJsonHandler handler;

	public DebugMessageTypeAdapterFactory(MessageJsonHandler handler) {
		this.handler = handler;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
		if (!Message.class.isAssignableFrom(typeToken.getRawType()))
			return null;
		return (TypeAdapter<T>) new Adapter(handler, gson);
	}

	private static class Adapter extends TypeAdapter<Message> {

		private final MessageJsonHandler handler;
		private final Gson gson;

		Adapter(MessageJsonHandler handler, Gson gson) {
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
			boolean success = true;
			Object rawParams = null;
			Object body = null;
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
					success = in.nextBoolean();
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
					message = in.nextString();
					break;
				}
				case "arguments": {
					rawParams = parseParams(in, method);
					break;
				}
				case "body": {
					if ("event".equals(messageType)) {
						body = parseParams(in, method);
					} else if ("response".equals(messageType)) {
						body = parseResult(in, request_seq, success);
					}
					break;
				}
				default:
					in.skipValue();
				}
			}
			id = request_seq != null ? request_seq : seq;
			in.endObject();
			Object params = parseParams(rawParams, method);
			return createMessage(messageType, id, method, success, message, params, body);
		}

		private Object parseResult(JsonReader in, String id, boolean success) {
			Object result;
			Type type = null;
			if (success) {
				MethodProvider methodProvider = handler.getMethodProvider();
				if (methodProvider != null && id != null) {
					String resolvedMethod = methodProvider.resolveMethod(id);
					if (resolvedMethod != null) {
						JsonRpcMethod jsonRpcMethod = handler.getJsonRpcMethod(resolvedMethod);
						if (jsonRpcMethod != null)
							type = jsonRpcMethod.getReturnType();
					}
				}
			}
			if (type == null)
				result = new JsonParser().parse(in);
			else
				result = gson.fromJson(in, type);
			return result;
		}

		protected Object parseParams(JsonReader in, String method) throws IOException {
			JsonToken next = in.peek();
			if (next == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			Type[] parameterTypes = getParameterTypes(method);
			if (parameterTypes.length == 1) {
				return fromJson(in, parameterTypes[0]);
			}
			if (parameterTypes.length > 1 && next == JsonToken.BEGIN_ARRAY) {
				List<Object> parameters = new ArrayList<Object>(parameterTypes.length);
				int index = 0;
				in.beginArray();
				while (in.hasNext()) {
					Type parameterType = index < parameterTypes.length ? parameterTypes[index] : null;
					Object parameter = fromJson(in, parameterType);
					parameters.add(parameter);
					index++;
				}
				in.endArray();
				while (index < parameterTypes.length) {
					parameters.add(null);
					index++;
				}
				return parameters;
			}
			return new JsonParser().parse(in);
		}

		protected Object parseParams(Object params, String method) {
			if (isNull(params)) {
				return null;
			}
			if (!(params instanceof JsonElement)) {
				return params;
			}
			JsonElement rawParams = (JsonElement) params;
			Type[] parameterTypes = getParameterTypes(method);
			if (parameterTypes.length == 1) {
				return fromJson(rawParams, parameterTypes[0]);
			}
			if (parameterTypes.length > 1 && rawParams instanceof JsonArray) {
				JsonArray array = (JsonArray) rawParams;
				List<Object> parameters = new ArrayList<Object>(Math.max(array.size(), parameterTypes.length));
				int index = 0;
				Iterator<JsonElement> iterator = array.iterator();
				while (iterator.hasNext()) {
					Type parameterType = index < parameterTypes.length ? parameterTypes[index] : null;
					Object parameter = fromJson(iterator.next(), parameterType);
					parameters.add(parameter);
					index++;
				}
				while (index < parameterTypes.length) {
					parameters.add(null);
					index++;
				}
				return parameters;
			}
			return rawParams;
		}

		protected Object fromJson(JsonReader in, Type type) {
			if (isNullOrVoidType(type)) {
				return new JsonParser().parse(in);
			}
			return gson.fromJson(in, type);
		}

		protected Object fromJson(JsonElement element, Type type) {
			if (isNull(element)) {
				return null;
			}
			if (isNullOrVoidType(type)) {
				return element;
			}
			Object value = gson.fromJson(element, type);
			if (isNull(value)) {
				return null;
			}
			return value;
		}

		protected boolean isNull(Object value) {
			return value == null || value instanceof JsonNull;
		}

		protected boolean isNullOrVoidType(Type type) {
			return type == null || Void.class == type;
		}

		protected Type[] getParameterTypes(String method) {
			if (method != null) {
				JsonRpcMethod jsonRpcMethod = handler.getJsonRpcMethod(method);
				if (jsonRpcMethod != null)
					return jsonRpcMethod.getParameterTypes();
			}
			return EMPTY_TYPE_ARRAY;
		}

		private Message createMessage(String messageType, String id, String method, boolean success,
				String errorMessage, Object params, Object body) {
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
					out.nullValue();
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
						out.nullValue();
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
						out.nullValue();
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
					out.nullValue();
				else
					gson.toJson(params, params.getClass(), out);
			}

			out.endObject();
		}

	}
}
