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

package org.springframework.cloud.kubernetes.client.leader.election;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.client.KubernetesClientAutoConfiguration;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.springframework.cloud.kubernetes.client.leader.election.KubernetesClientLeaderElectionUtil.apiClientWithLeaseSupport;
import static org.springframework.cloud.kubernetes.client.leader.election.KubernetesClientLeaderElectionUtil.wireMockServer;

/**
 * @author wind57
 */
class KubernetesClientLeaderElectionAutoConfigurationTests {

	private static WireMockServer wireMockServer;

	@BeforeAll
	static void beforeAll() {
		wireMockServer = wireMockServer();
	}

	@AfterAll
	static void after() {
		wireMockServer.stop();
	}

	@AfterEach
	void afterEach() {
		WireMock.reset();
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election is not present
	 *
	 *     As such:
	 *
	 *     - KubernetesClientLeaderElectionAutoConfiguration          is not present
	 *     - KubernetesClientLeaderElectionCallbacksAutoConfiguration is not present
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationMissing() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(KubernetesCommonsAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, KubernetesClientLeaderElectionAutoConfiguration.class,
					KubernetesClientLeaderElectionCallbacksAutoConfiguration.class))
			.withAllowBeanDefinitionOverriding(true)
			.withUserConfiguration(ApiClientConfiguration.class)
			.run(context -> {
				Assertions.assertThat(context).doesNotHaveBean(KubernetesClientLeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context)
					.doesNotHaveBean(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = false
	 *
	 *     As such:
	 *
	 *     - KubernetesClientLeaderElectionAutoConfiguration          is not present
	 *     - KubernetesClientLeaderElectionCallbacksAutoConfiguration is not present
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationPresentEqualToFalse() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(KubernetesCommonsAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, KubernetesClientLeaderElectionAutoConfiguration.class,
					KubernetesClientLeaderElectionCallbacksAutoConfiguration.class))
			.withAllowBeanDefinitionOverriding(true)
			.withUserConfiguration(ApiClientConfiguration.class)
			.withPropertyValues("spring.cloud.kubernetes.leader.election.enabled=false")
			.run(context -> {
				Assertions.assertThat(context).doesNotHaveBean(KubernetesClientLeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context)
					.doesNotHaveBean(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = true
	 *
	 *     As such:
	 *
	 *     - KubernetesClientLeaderElectionAutoConfiguration          is present
	 *     - KubernetesClientLeaderElectionCallbacksAutoConfiguration is present
	 * </pre>
	 */
	@Test
	void leaderElectionAnnotationPresentEqualToTrue() {
		new ApplicationContextRunner().withAllowBeanDefinitionOverriding(true)
			.withUserConfiguration(ApiClientConfiguration.class)
			.withConfiguration(AutoConfigurations.of(KubernetesCommonsAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, KubernetesClientLeaderElectionAutoConfiguration.class,
					KubernetesClientLeaderElectionCallbacksAutoConfiguration.class))
			.withPropertyValues("spring.cloud.kubernetes.leader.election.enabled=true",
					"spring.main.cloud-platform=kubernetes")
			.run(context -> {
				Assertions.assertThat(context).hasSingleBean(KubernetesClientLeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context)
					.hasSingleBean(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = true
	 *     - management.info.leader.election.enabled = true
	 *
	 *     As such:
	 *
	 *     - KubernetesClientLeaderElectionAutoConfiguration          is present
	 *     - KubernetesClientLeaderElectionCallbacksAutoConfiguration is present
	 *     - KubernetesClientLeaderElectionInfoContributor            is present
	 * </pre>
	 */
	@Test
	void leaderInfoContributorPresent() {
		new ApplicationContextRunner().withUserConfiguration(ApiClientConfiguration.class)
			.withConfiguration(AutoConfigurations.of(KubernetesCommonsAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, KubernetesClientLeaderElectionAutoConfiguration.class,
					KubernetesClientLeaderElectionCallbacksAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=kubernetes", "management.info.leader.election.enabled=true",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> {
				Assertions.assertThat(context).hasSingleBean(KubernetesClientLeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context)
					.hasSingleBean(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class);
				Assertions.assertThat(context).hasSingleBean(KubernetesClientLeaderElectionInfoContributor.class);
			});
	}

	/**
	 * <pre>
	 *     - spring.cloud.kubernetes.leader.election = true
	 *     - management.info.leader.election.enabled = false
	 *
	 *     As such:
	 *
	 *     - KubernetesClientLeaderElectionAutoConfiguration          is present
	 *     - KubernetesClientLeaderElectionCallbacksAutoConfiguration is present
	 *     - KubernetesClientLeaderElectionInfoContributor            is not present
	 * </pre>
	 */
	@Test
	void leaderInfoContributorMissing() {
		new ApplicationContextRunner().withUserConfiguration(ApiClientConfiguration.class)
			.withConfiguration(AutoConfigurations.of(KubernetesCommonsAutoConfiguration.class,
					KubernetesClientAutoConfiguration.class, KubernetesClientLeaderElectionAutoConfiguration.class,
					KubernetesClientLeaderElectionCallbacksAutoConfiguration.class))
			.withPropertyValues("spring.main.cloud-platform=kubernetes",
					"management.info.leader.election.enabled=false",
					"spring.cloud.kubernetes.leader.election.enabled=true")
			.run(context -> {
				Assertions.assertThat(context).hasSingleBean(KubernetesClientLeaderElectionAutoConfiguration.class);
				Assertions.assertThat(context)
					.hasSingleBean(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class);
				Assertions.assertThat(context).doesNotHaveBean(KubernetesClientLeaderElectionInfoContributor.class);
			});
	}

	@Configuration
	static class ApiClientConfiguration {

		@Bean
		@Primary
		ApiClient apiClient() {
			return apiClientWithLeaseSupport(wireMockServer);
		}

	}

}
