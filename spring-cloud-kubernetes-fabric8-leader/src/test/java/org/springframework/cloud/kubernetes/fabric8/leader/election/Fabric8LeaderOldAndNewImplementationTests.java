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

package org.springframework.cloud.kubernetes.fabric8.leader.election;

import io.fabric8.kubernetes.api.model.APIGroupList;
import io.fabric8.kubernetes.api.model.APIGroupListBuilder;
import io.fabric8.kubernetes.api.model.APIResourceBuilder;
import io.fabric8.kubernetes.api.model.APIResourceListBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscoveryBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.commons.KubernetesCommonsAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.Fabric8AutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.leader.Fabric8LeaderAutoConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that prove that previous and new leader implementation works based on the flags
 * we set.
 *
 * @author wind57
 */
class Fabric8LeaderOldAndNewImplementationTests {

	private ApplicationContextRunner applicationContextRunner;

	/**
	 * <pre>
	 *     - 'spring.cloud.kubernetes.leader.enabled'           is not set
	 *     - 'spring.cloud.kubernetes.leader.election.enabled'  is not set
	 *
	 *     As such :
	 *
	 *     - 'Fabric8LeaderAutoConfiguration'                   is active
	 *     - 'Fabric8LeaderElectionAutoConfiguration'           is not active
	 * </pre>
	 */
	@Test
	void noFlagsSet() {
		setup("spring.main.cloud-platform=KUBERNETES");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(Fabric8LeaderAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
		});
	}

	/**
	 * <pre>
	 *     - 'spring.cloud.kubernetes.leader.enabled'          =  true
	 *     - 'spring.cloud.kubernetes.leader.election.enabled'    is not set
	 *
	 *     As such :
	 *
	 *     - 'Fabric8LeaderAutoConfiguration'                   is active
	 *     - 'Fabric8LeaderElectionAutoConfiguration'           is not active
	 * </pre>
	 */
	@Test
	void oldImplementationEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(Fabric8LeaderAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
		});
	}

	/**
	 * <pre>
	 *     - 'spring.cloud.kubernetes.leader.enabled'          = false
	 *     - 'spring.cloud.kubernetes.leader.election.enabled'   is not set
	 *
	 *     As such :
	 *
	 *     - 'Fabric8LeaderAutoConfiguration'                   is not active
	 *     - 'Fabric8LeaderElectionAutoConfiguration'           is not active
	 * </pre>
	 */
	@Test
	void oldImplementationDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8LeaderAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
		});
	}

	/**
	 * <pre>
	 *     - 'spring.cloud.kubernetes.leader.enabled'            is not set
	 *     - 'spring.cloud.kubernetes.leader.election.enabled' = false
	 *
	 *     As such :
	 *
	 *     - 'Fabric8LeaderAutoConfiguration'                   is active
	 *     - 'Fabric8LeaderElectionAutoConfiguration'           is not active
	 * </pre>
	 */
	@Test
	void newImplementationDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).hasSingleBean(Fabric8LeaderAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
		});
	}

	/**
	 * <pre>
	 *     - 'spring.cloud.kubernetes.leader.enabled'            is not set
	 *     - 'spring.cloud.kubernetes.leader.election.enabled' = true
	 *
	 *     As such :
	 *
	 *     - 'Fabric8LeaderAutoConfiguration'                   is not active
	 *     - 'Fabric8LeaderElectionAutoConfiguration'           is active
	 * </pre>
	 */
	@Test
	void newImplementationEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.election.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8LeaderAutoConfiguration.class);
			assertThat(context).hasSingleBean(Fabric8LeaderElectionAutoConfiguration.class);
		});
	}

	/**
	 * <pre>
	 *     - 'spring.cloud.kubernetes.leader.enabled'          = false
	 *     - 'spring.cloud.kubernetes.leader.election.enabled' = false
	 *
	 *     As such :
	 *
	 *     - 'Fabric8LeaderAutoConfiguration'                   is not active
	 *     - 'Fabric8LeaderElectionAutoConfiguration'           is not active
	 * </pre>
	 */
	@Test
	void bothDisabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.enabled=false",
				"spring.cloud.kubernetes.leader.election.enabled=false");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8LeaderAutoConfiguration.class);
			assertThat(context).doesNotHaveBean(Fabric8LeaderElectionAutoConfiguration.class);
		});
	}

	/**
	 * <pre>
	 *     - 'spring.cloud.kubernetes.leader.enabled'          = true
	 *     - 'spring.cloud.kubernetes.leader.election.enabled' = true
	 *
	 *     As such :
	 *
	 *     - 'Fabric8LeaderAutoConfiguration'                   is not active
	 *     - 'Fabric8LeaderElectionAutoConfiguration'           is active
	 *
	 *     You can't enable both of them, only the new one will work.
	 * </pre>
	 */
	@Test
	void bothEnabled() {
		setup("spring.main.cloud-platform=KUBERNETES", "spring.cloud.kubernetes.leader.enabled=true",
				"spring.cloud.kubernetes.leader.election.enabled=true");
		applicationContextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(Fabric8LeaderAutoConfiguration.class);
			assertThat(context).hasSingleBean(Fabric8LeaderElectionAutoConfiguration.class);
		});
	}

	private void setup(String... properties) {
		applicationContextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(Fabric8LeaderElectionCallbacksAutoConfiguration.class,
					Fabric8AutoConfiguration.class, KubernetesCommonsAutoConfiguration.class,
					Fabric8LeaderElectionAutoConfiguration.class, Fabric8LeaderAutoConfiguration.class))
			.withUserConfiguration(Fabric8LeaderOldAndNewImplementationTests.Configuration.class)
			.withPropertyValues(properties);
	}

	@TestConfiguration
	static class Configuration {

		@Bean
		@SuppressWarnings({ "rawtypes", "unchecked" })
		KubernetesClient mockKubernetesClient() {
			KubernetesClient client = Mockito.mock(KubernetesClient.class);

			Mockito.when(client.getNamespace()).thenReturn("namespace");

			MixedOperation mixedOperation = Mockito.mock(MixedOperation.class);
			NonNamespaceOperation nonNamespaceOperation = Mockito.mock(NonNamespaceOperation.class);
			Mockito.when(client.configMaps()).thenReturn(mixedOperation);

			Mockito.when(mixedOperation.inNamespace(Mockito.anyString())).thenReturn(nonNamespaceOperation);
			Resource<ConfigMap> configMapResource = Mockito.mock(Resource.class);
			Mockito.when(nonNamespaceOperation.withName(Mockito.anyString())).thenReturn(configMapResource);

			Mockito.when(client.pods()).thenReturn(mixedOperation);
			PodResource podResource = Mockito.mock(PodResource.class);
			Mockito.when(mixedOperation.withName(Mockito.anyString())).thenReturn(podResource);

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
