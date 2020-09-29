/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.config.example2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableConfigurationProperties(ExampleAppProps.class)
public class ExampleApp {

	public static void main(String[] args) {
		SpringApplication.run(org.springframework.cloud.kubernetes.config.example.App.class, args);
	}

	@RestController
	public static class Controller {

		private final ExampleAppProps exampleAppProps;

		public Controller(ExampleAppProps exampleAppProps) {
			this.exampleAppProps = exampleAppProps;
		}

		@GetMapping("/common")
		public Response commonMessage() {
			return new Response(this.exampleAppProps.getCommonMessage());
		}

		@GetMapping("/m1")
		public Response message1() {
			return new Response(this.exampleAppProps.getMessage1());
		}

		@GetMapping("/m2")
		public Response message2() {
			return new Response(this.exampleAppProps.getMessage2());
		}

		@GetMapping("/m3")
		public Response message3() {
			return new Response(this.exampleAppProps.getMessage3());
		}

	}

	public static class Response {

		private final String message;

		public Response(String message) {
			this.message = message;
		}

		public String getMessage() {
			return this.message;
		}

	}

}
