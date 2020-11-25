/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ryan Baxter
 */
@SpringBootApplication
@RestController
public class KubernetesConfigClientApplicationIt {

	@Autowired
	private MyConfigurationProperties configurationProperties;

	@GetMapping("/myProperty")
	public String myProperty() {
		return configurationProperties.getMyProperty();
	}

	@GetMapping("/mySecret")
	public String mySecret() {
		return configurationProperties.getMySecret();
	}

	public static void main(String[] args) {
		SpringApplication.run(KubernetesConfigClientApplicationIt.class, args);
	}

	@Configuration
	@EnableConfigurationProperties(MyConfigurationProperties.class)
	class MyConfig {

	}

}
