/*
 * Copyright (C) 2016 to the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.config.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author <a href="mailto:cmoullia@redhat.com">Charles Moulliard</a>
 */
@RestController
public class GreetingController {

	private final GreetingProperties properties;

	@Autowired
	public GreetingController(GreetingProperties properties) {
		this.properties = properties;
	}

	@RequestMapping("/api/greeting")
	public Greeting greeting(@RequestParam(value="name", defaultValue="World") String name) {
		String message = String.format(properties.getGreeting(), name);
		return new Greeting(message);
	}

	@RequestMapping("/api/farewell")
	public Greeting farewell(@RequestParam(value="name", defaultValue="World") String name) {
		String message = String.format(properties.getFarewell(), name);
		return new Greeting(message);
	}
}
