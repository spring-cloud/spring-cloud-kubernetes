/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.configuration.watcher;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesClientInformerReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.AMQP;
import static org.springframework.cloud.kubernetes.configuration.watcher.ConfigurationWatcherConfigurationProperties.KAFKA;

/**
 * @author wind57
 */
class RefreshTriggerAutoConfigurationTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void amqpOnly() {
		setup(AMQP);
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(BusRefreshTrigger.class);
			assertThat(context).doesNotHaveBean(HttpRefreshTrigger.class);
		});
	}

	@Test
	void kafkaOnly() {
		setup(KAFKA);
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(BusRefreshTrigger.class);
			assertThat(context).doesNotHaveBean(HttpRefreshTrigger.class);
		});
	}

	@Test
	void kafkaAndAmqp() {
		setup(KAFKA + " , " + AMQP);
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(BusRefreshTrigger.class);
			assertThat(context).doesNotHaveBean(HttpRefreshTrigger.class);
		});
	}

	@Test
	void notAmqp() {
		setup("not-" + AMQP);
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(BusRefreshTrigger.class);
			assertThat(context).hasSingleBean(HttpRefreshTrigger.class);
		});
	}

	@Test
	void notKafka() {
		setup("not-" + KAFKA);
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(BusRefreshTrigger.class);
			assertThat(context).hasSingleBean(HttpRefreshTrigger.class);
		});
	}

	@Test
	void amqpNotKafka() {
		setup(AMQP + "," + "not-" + KAFKA);
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(BusRefreshTrigger.class);
			assertThat(context).doesNotHaveBean(HttpRefreshTrigger.class);
		});
	}

	@Test
	void kafkaNotAmqp() {
		setup(KAFKA + "," + "not-" + AMQP);
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(BusRefreshTrigger.class);
			assertThat(context).doesNotHaveBean(HttpRefreshTrigger.class);
		});
	}

	private void setup(String activeProfiles) {
		applicationContextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class, RefreshTriggerAutoConfiguration.class)
			.withPropertyValues("spring.main.cloud-platform=kubernetes", "spring.profiles.active=" + activeProfiles);
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		BusProperties busProperties() {
			return new BusProperties();
		}

		@Bean
		@Primary
		KubernetesClientInformerReactiveDiscoveryClient client() {
			return Mockito.mock(KubernetesClientInformerReactiveDiscoveryClient.class);
		}

		@Bean
		@Primary
		ConfigurationWatcherConfigurationProperties configurationWatcherConfigurationProperties() {
			return new ConfigurationWatcherConfigurationProperties();
		}

		@Primary
		@Bean
		WebClient webClient() {
			return Mockito.mock(WebClient.class);
		}

	}

}
