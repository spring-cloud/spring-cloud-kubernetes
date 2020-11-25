/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.example3;

/**
 * @author Haytham Mohamed
 **/

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableConfigurationProperties(MultiSecretsProperties.class)
public class MultiSecretsApp {

	public static void main(String[] args) {
		SpringApplication.run(MultiSecretsApp.class, args);
	}

	@RestController
	public static class Controller {

		private final MultiSecretsProperties properties;

		public Controller(MultiSecretsProperties properties) {
			this.properties = properties;
		}

		@GetMapping("/secret1")
		public Response secret1() {
			return new Response(this.properties.getSecret1());
		}

		@GetMapping("/secret2")
		public Response secret2() {
			return new Response(this.properties.getSecret2());
		}

	}

	public static class Response {

		private final String secret;

		public Response(String secret) {
			this.secret = secret;
		}

		public String getSecret() {
			return this.secret;
		}

	}

}
