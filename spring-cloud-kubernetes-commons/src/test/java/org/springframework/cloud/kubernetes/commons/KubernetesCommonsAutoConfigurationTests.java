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

package org.springframework.cloud.kubernetes.commons;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		classes = KubernetesCommonsAutoConfigurationTests.App.class,
		properties = { "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.client.password=mypassword",
				"spring.cloud.kubernetes.client.proxy-password=myproxypassword", "spring.cloud.config.enabled=false" })
class KubernetesCommonsAutoConfigurationTests {

	@Autowired
	ConfigurableApplicationContext context;

	@Test
	void beansAreCreated() {
		assertThat(context.getBeansOfType(KubernetesClientProperties.class)).hasSize(1);

		KubernetesClientProperties properties = context.getBeansOfType(KubernetesClientProperties.class).values()
				.stream().findFirst().get();
		assertThat(properties.password()).isEqualTo("mypassword");
		assertThat(properties.proxyPassword()).isEqualTo("myproxypassword");
	}

	@SpringBootApplication
	static class App {

	}

}
