/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.kubernetes.it;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class SimpleCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleCoreApplication.class, args);
	}

	@Value("${greeting.message}")
	private String message;

	@GetMapping("/greeting")
	public Greeting home() {
		return new Greeting(message);
	}

	public static class Greeting {

		private final String message;

		public Greeting(String message) {
			this.message = message;
		}

		public String getMessage() {
			return this.message;
		}

	}

}
