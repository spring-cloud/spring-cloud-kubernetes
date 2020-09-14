/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.examples;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Name service controller.
 *
 * @author Gytis Trikleris
 * @author Olga Maciaszek-Sharma
 */
@RestController
public class NameController {

	private static final Logger LOG = LoggerFactory.getLogger(NameController.class);

	private final String hostName = System.getenv("HOSTNAME");

	@GetMapping("/")
	public String ribbonPing() {
		LOG.info("Ribbon ping");
		return hostName;
	}

	/**
	 * Endpoint to get a name with a capability to delay a response for some number of
	 * milliseconds.
	 * @param delayValue Milliseconds for how long the response should be delayed.
	 * @return Host name.
	 */
	@GetMapping("/name")
	public Mono<String> getName(
			@RequestParam(value = "delay", defaultValue = "0") int delayValue) {
		LOG.info(String.format("Returning a name '%s' with a delay '%d'", hostName,
				delayValue));
		delay(delayValue);
		return Mono.just(hostName);
	}

	private void delay(int delayValue) {
		try {
			Thread.sleep(delayValue);
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
