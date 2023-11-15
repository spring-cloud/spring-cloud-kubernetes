/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesDiscoveryAutoConfigurationTests {

	/**
	 * <pre>
	 *     '@ConditionalOnDiscoveryEnabled' is not matched, thus no beans are created
	 *     from either blocking or reactive configurations.
	 * </pre>
	 */
	@Test
	void discoveryEnabledDefault() {
		setupWithFilteredClassLoader(null, "spring.main.cloud-platform=KUBERNETES",
			"spring.cloud.discovery.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(RestTemplate.class);
			assertThat(context).doesNotHaveBean(DiscoveryClient.class);

			assertThat(context).doesNotHaveBean(WebClient.Builder.class);
			assertThat(context).doesNotHaveBean(ReactiveDiscoveryClient.class);

		});
	}

	private ApplicationContextRunner applicationContextRunner;

	private void setupWithFilteredClassLoader(Class<?> cls, String... properties) {

		if (cls != null) {
			applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesDiscoveryClientBlockingAutoConfiguration.class,
					KubernetesDiscoveryClientReactiveAutoConfiguration.class))
				.withClassLoader(new FilteredClassLoader(cls))
				.withPropertyValues(properties);
		}
		else {
			applicationContextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(KubernetesDiscoveryClientBlockingAutoConfiguration.class,
					KubernetesDiscoveryClientReactiveAutoConfiguration.class))
				.withPropertyValues(properties);
		}

	}

}
