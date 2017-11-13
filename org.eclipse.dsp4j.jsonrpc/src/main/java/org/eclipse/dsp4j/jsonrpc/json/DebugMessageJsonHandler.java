/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.dsp4j.jsonrpc.json;

import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.dsp4j.jsonrpc.adapters.DebugMessageTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.adapters.CollectionTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EnumTypeAdapterFactory;

import com.google.gson.GsonBuilder;

public class DebugMessageJsonHandler extends MessageJsonHandler {
	public DebugMessageJsonHandler(Map<String, JsonRpcMethod> supportedMethods) {
		super(supportedMethods);
	}

	public DebugMessageJsonHandler(Map<String, JsonRpcMethod> supportedMethods, Consumer<GsonBuilder> configureGson) {
		super(supportedMethods, configureGson);
	}

	public GsonBuilder getDefaultGsonBuilder() {
		return new GsonBuilder().registerTypeAdapterFactory(new CollectionTypeAdapterFactory())
				.registerTypeAdapterFactory(new EitherTypeAdapterFactory())
				.registerTypeAdapterFactory(new EnumTypeAdapterFactory())
				.registerTypeAdapterFactory(new DebugMessageTypeAdapterFactory(this));
	}

}
