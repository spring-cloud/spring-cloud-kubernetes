/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.configuration.watcher;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.stream.messaging.DirectWithAttributesChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

final class GlobalInterceptor implements ChannelInterceptor {

	private static final Log LOG = LogFactory.getLog(GlobalInterceptor.class);

	@Override
	public Message<?> preSend(Message<?> msg, MessageChannel mc) {
		DirectWithAttributesChannel ch = ((DirectWithAttributesChannel) mc);
		LOG.debug("getFullChannelName : " + ch.getFullChannelName());
		LOG.debug("getBeanName : " + ch.getBeanName());
		LOG.debug("getApplicationContextId : " + ch.getApplicationContextId());
		LOG.debug("In preSend " + msg.getPayload() + " for channel ");
		return msg;
	}

	@Override
	public void postSend(Message<?> msg, MessageChannel mc, boolean bln) {
		LOG.debug("In postSend " + msg.getPayload() + " for channel " + ((DirectWithAttributesChannel) mc).toString());
	}

	@Override
	public void afterSendCompletion(Message<?> msg, MessageChannel mc, boolean bln, Exception excptn) {
		LOG.debug("In afterSendCompletion " + msg.getPayload() + " for channel " + mc.getClass().getName());
	}

	@Override
	public boolean preReceive(MessageChannel mc) {
		LOG.debug("In preReceive for channel " + mc.getClass().getName());
		return true;
	}

	@Override
	public Message<?> postReceive(Message<?> msg, MessageChannel mc) {
		LOG.debug("In postReceive " + msg.getPayload() + " for channel " + mc.getClass().getName());
		return msg;
	}

	@Override
	public void afterReceiveCompletion(Message<?> msg, MessageChannel mc, Exception exception) {
		LOG.debug("In afterReceiveCompletion", exception);
	}

}
