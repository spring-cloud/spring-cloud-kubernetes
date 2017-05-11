/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.springframework.cloud.kubernetes.examples;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Greeting service controller.
 *
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RestController
public class GreetingController {

	private final NameService nameService;

	public GreetingController(NameService nameService) {
		this.nameService = nameService;
	}

	/**
	 * Endpoint to get a greeting. This endpoint uses a name server to get a name for the greeting.
	 *
	 * Request to the name service is guarded with a circuit breaker. Therefore if a name service is not available or is too
	 * slow to response fallback name is used.
	 *
	 * Delay parameter can me used to make name service response slower.
	 *
	 * @param delay Milliseconds for how long the response from name service should be delayed.
	 * @return Greeting string.
	 */
	@RequestMapping("/greeting")
	public String getGreeting(@RequestParam(value = "delay", defaultValue = "0") int delay) {
		return String.format("Hello from %s!", this.nameService.getName(delay));
	}

}
