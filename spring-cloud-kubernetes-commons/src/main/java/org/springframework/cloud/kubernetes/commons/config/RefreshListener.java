/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.log.LogAccessor;

/**
 * listener that is supposed to catch calls from /actuator/refresh. This is mainly needed
 * for when configuration-watcher calls refresh endpoint.
 *
 * @author wind57
 */
public final class RefreshListener {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(RefreshListener.class));

	@EventListener(RefreshScopeRefreshedEvent.class)
	public void onRefresh(RefreshScopeRefreshedEvent event) {
		LOG.debug(() -> "caught event with name : " + event.getName() + " from source " + event.getSource() + " "
				+ "at : "
				+ LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimestamp()), ZoneId.systemDefault()));
		LOG.info("refresh endpoint called, reloading configmap/secret based property sources.");
	}

}
