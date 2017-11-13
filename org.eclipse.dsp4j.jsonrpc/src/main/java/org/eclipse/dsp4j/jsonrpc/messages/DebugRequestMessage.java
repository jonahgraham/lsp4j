/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.dsp4j.jsonrpc.messages;

import org.eclipse.dsp4j.jsonrpc.adapters.DebugMessageTypeAdapterFactory;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;

/**
 * DSP specific version of RequestMessage.
 *
 * @see DebugMessageTypeAdapterFactory
 */
public class DebugRequestMessage extends RequestMessage {
	// no additional fields are needed to represent request messages in DSP
}
