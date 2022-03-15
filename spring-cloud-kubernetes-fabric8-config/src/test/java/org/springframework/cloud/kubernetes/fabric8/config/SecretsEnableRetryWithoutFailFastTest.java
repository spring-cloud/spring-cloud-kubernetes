/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.info.InfoEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.config.KubernetesBootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.RetryConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
@EnableKubernetesMockClient
public class SecretsEnableRetryWithoutFailFastTest {

	private static final String API = "/api/v1/namespaces/default/configmaps/application";

	static KubernetesMockServer mockServer;

	private ConfigurableApplicationContext context;

	protected void setup(String... env) {
		List<String> envList = (env != null) ? new ArrayList<>(Arrays.asList(env)) : new ArrayList<>();
		envList.add("spring.cloud.kubernetes.client.namespace=default");
		String[] envArray = envList.toArray(new String[0]);

		context = new SpringApplicationBuilder(RetryConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
				ConfigReloadAutoConfiguration.class, RefreshAutoConfiguration.class, EndpointAutoConfiguration.class,
				InfoEndpointAutoConfiguration.class, RefreshEndpointAutoConfiguration.class,
				ConfigurationPropertiesBindingPostProcessor.class,
				ConfigurationPropertiesRebinderAutoConfiguration.class, Fabric8BootstrapConfiguration.class,
				Fabric8RetryBootstrapConfiguration.class, KubernetesBootstrapConfiguration.class)
						.web(org.springframework.boot.WebApplicationType.NONE).properties(envArray).run();
	}

	@AfterEach
	public void afterEach() {
		if (this.context != null) {
			this.context.close();
			this.context = null;
		}
	}

	@Test
	public void doesNotContainRetryableSecretsPropertySourceLocator() throws Exception {
		mockServer.expect().withPath(API).andReturn(500, "Internal Server Error").once();
		setup("debug=true", "spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.test.enable-retry=true",
				"spring.cloud.kubernetes.secrets.name=my-secret", "spring.cloud.kubernetes.secrets.enable-api=true");
		assertThat(context.containsBean("retryableSecretsPropertySourceLocator")).isFalse();
	}

}
