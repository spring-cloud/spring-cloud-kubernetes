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

package org.springframework.cloud.kubernetes.configuration.watcher.appA;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@SpringBootApplication
@RestController
public class AppATestApplication implements ApplicationListener<RefreshRemoteApplicationEvent> {

	private final Log LOG = LogFactory.getLog(getClass());

	private boolean value = false;

	public static void main(String[] args) {
		SpringApplication.run(AppATestApplication.class, args);
	}

	@GetMapping("/app-a")
	public boolean index() {
		LOG.info("Current value: " + value);
		return value;
	}

	@Override
	public void onApplicationEvent(RefreshRemoteApplicationEvent refreshRemoteApplicationEvent) {
		LOG.info("Received remote refresh event from origin: " + refreshRemoteApplicationEvent.getOriginService()
				+ " to destination : " + refreshRemoteApplicationEvent.getDestinationService());
		this.value = true;
	}

}
