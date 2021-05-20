/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.istio;

import me.snowdrop.istio.client.IstioClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
public class IstioAutoConfigurationTests {

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class)
	@Nested
	class IstioClientPresentByDefault {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void istioClientIsPresent() {
			assertThat(context.getBeanNamesForType(IstioClient.class)).hasSize(1);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
			properties = { "spring.cloud.kubernetes.enabled=false" })
	@Nested
	class IstioClientNotPresentWhenKubernetesDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void istioClientNotPresent() {
			assertThat(context.getBeanNamesForType(IstioClient.class)).hasSize(0);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
			properties = { "spring.cloud.istio.enabled=true" })
	@Nested
	class IstioClientPresentWhenIstioEnabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void istioClientIsPresent() {
			assertThat(context.getBeanNamesForType(IstioClient.class)).hasSize(1);
		}

	}

	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
			properties = { "spring.cloud.istio.enabled=false" })
	@Nested
	class IstioClientNotPresentPresentWhenIstioDisabled {

		@Autowired
		ConfigurableApplicationContext context;

		@Test
		public void istioClientNotPresent() {
			assertThat(context.getBeanNamesForType(IstioClient.class)).hasSize(0);
		}

	}

}
