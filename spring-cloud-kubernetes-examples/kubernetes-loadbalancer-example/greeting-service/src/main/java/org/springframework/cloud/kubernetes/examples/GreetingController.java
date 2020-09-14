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

import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Greeting service controller.
 *
 * @author Gytis Trikleris
 * @author Olga Maciaszek-Sharma
 */
@RestController
public class GreetingController {

	private final NameService nameService;

	public GreetingController(NameService nameService) {
		this.nameService = nameService;
	}

	/**
	 * Endpoint to get a greeting. This endpoint uses a name server to get a name for the
	 * greeting.
	 *
	 * Request to the name service is guarded with a circuit breaker. Therefore if a name
	 * service is not available or is too slow to response fallback name is used.
	 *
	 * Delay parameter can me used to make name service response slower.
	 * @param delay Milliseconds for how long the response from name service should be
	 * delayed.
	 * @return Greeting string.
	 */
	@GetMapping("/greeting")
	public Mono<String> getGreeting(
			@RequestParam(value = "delay", defaultValue = "0") int delay) {
		return nameService.getName(delay)
				.map(name -> String.format("Hello from %s!", name));
	}

}
