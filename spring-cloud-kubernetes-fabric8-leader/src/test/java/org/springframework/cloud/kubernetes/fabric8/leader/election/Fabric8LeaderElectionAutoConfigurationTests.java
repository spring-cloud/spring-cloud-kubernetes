/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import io.fabric8.kubernetes.api.model.APIGroupList;
import io.fabric8.kubernetes.api.model.APIGroupListBuilder;
import io.fabric8.kubernetes.api.model.APIResourceBuilder;
import io.fabric8.kubernetes.api.model.APIResourceListBuilder;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscoveryBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.Lock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class Fabric8LeaderElectionAutoConfigurationTests {

	private ApplicationContextRunner applicationContextRunner;

	@Test
	void allBeansPresent() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(Fabric8LeaderElectionInfoContributor.class);
			assertThat(context).hasSingleBean(Fabric8LeaderElectionInitiator.class);
			assertThat(context).hasSingleBean(LeaderElectionConfig.class);
			assertThat(context).hasSingleBean(Lock.class);
			assertThat(context).hasSingleBean(Fabric8LeaderElectionCallbacks.class);
		});
	}

	@Test
	void allBeansPresentWithoutHealthIndicator() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=true",
				"management.health.leader.election.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionInfoContributor.class);
			assertThat(context).hasSingleBean(Fabric8LeaderElectionInitiator.class);
			assertThat(context).hasSingleBean(LeaderElectionConfig.class);
			assertThat(context).hasSingleBean(Lock.class);
			assertThat(context).hasSingleBean(Fabric8LeaderElectionCallbacks.class);
		});
	}

	@Test
	void allBeansMissing() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionInfoContributor.class);
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionInitiator.class);
			assertThat(context).doesNotHaveBean(LeaderElectionConfig.class);
			assertThat(context).doesNotHaveBean(Lock.class);
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionCallbacks.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(Fabric8LeaderElectionCallbacksAutoConfiguration.class,
					Fabric8AutoConfiguration.class, KubernetesCommonsAutoConfiguration.class,
					Fabric8LeaderElectionAutoConfiguration.class))
			.withUserConfiguration(Configuration.class)
			.withPropertyValues(properties);
	}

	@TestConfiguration
	static class Configuration {

		@Bean
		KubernetesClient mockKubernetesClient() {
			KubernetesClient client = Mockito.mock(KubernetesClient.class);

			Mockito.when(client.getApiResources("coordination.k8s.io/v1"))
				.thenReturn(
						new APIResourceListBuilder().withResources(new APIResourceBuilder().withKind("Lease").build())
							.build());

			APIGroupList apiGroupList = new APIGroupListBuilder().addNewGroup()
				.withVersions(new GroupVersionForDiscoveryBuilder().withGroupVersion("coordination.k8s.io/v1").build())
				.endGroup()
				.build();

			Mockito.when(client.getApiGroups()).thenReturn(apiGroupList);
			return client;
		}

	}

}
