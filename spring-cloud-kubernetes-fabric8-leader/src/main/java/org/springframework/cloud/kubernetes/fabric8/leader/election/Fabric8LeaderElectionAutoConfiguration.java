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

import java.util.function.BooleanSupplier;

import io.fabric8.kubernetes.api.model.APIResource;
import io.fabric8.kubernetes.api.model.APIResourceList;
import io.fabric8.kubernetes.api.model.GroupVersionForDiscovery;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.ConfigMapLock;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.Lock;
import io.fabric8.kubernetes.client.readiness.Readiness;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.cloud.kubernetes.commons.leader.election.ConditionalOnLeaderElectionEnabled;
import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.leader.election.LeaderUtils.COORDINATION_GROUP;
import static org.springframework.cloud.kubernetes.commons.leader.election.LeaderUtils.COORDINATION_VERSION;
import static org.springframework.cloud.kubernetes.commons.leader.election.LeaderUtils.LEASE;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LeaderElectionProperties.class)
@ConditionalOnBean(KubernetesClient.class)
@ConditionalOnLeaderElectionEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureAfter(Fabric8LeaderElectionCallbacksAutoConfiguration.class)
class Fabric8LeaderElectionAutoConfiguration {

	private static final String COORDINATION_VERSION_GROUP = COORDINATION_GROUP + "/" + COORDINATION_VERSION;

	private static final LogAccessor LOG = new LogAccessor(Fabric8LeaderElectionAutoConfiguration.class);

	@Bean
	@ConditionalOnClass(InfoContributor.class)
	@ConditionalOnEnabledHealthIndicator("leader.election")
	Fabric8LeaderElectionInfoContributor leaderElectionInfoContributor(String candidateIdentity,
			LeaderElectionConfig leaderElectionConfig, KubernetesClient fabric8KubernetesClient) {
		return new Fabric8LeaderElectionInfoContributor(candidateIdentity, leaderElectionConfig,
				fabric8KubernetesClient);
	}

	@Bean
	@ConditionalOnMissingBean
	Fabric8LeaderElectionInitiator fabric8LeaderElectionInitiator(String candidateIdentity, String podNamespace,
			KubernetesClient fabric8KubernetesClient, LeaderElectionConfig fabric8LeaderElectionConfig,
			LeaderElectionProperties leaderElectionProperties, BooleanSupplier podReadySupplier) {
		return new Fabric8LeaderElectionInitiator(candidateIdentity, podNamespace, fabric8KubernetesClient,
				fabric8LeaderElectionConfig, leaderElectionProperties, podReadySupplier);
	}

	@Bean
	BooleanSupplier podReadySupplier(KubernetesClient fabric8KubernetesClient, String candidateIdentity,
			String podNamespace) {
		return () -> {
			Pod pod = fabric8KubernetesClient.pods().inNamespace(podNamespace).withName(candidateIdentity).get();
			return Readiness.isPodReady(pod);
		};
	}

	@Bean
	@ConditionalOnMissingBean
	LeaderElectionConfig fabric8LeaderElectionConfig(LeaderElectionProperties properties, Lock lock,
			Fabric8LeaderElectionCallbacks fabric8LeaderElectionCallbacks) {
		return new LeaderElectionConfigBuilder().withReleaseOnCancel()
			.withName("Spring k8s leader election")
			.withLeaseDuration(properties.leaseDuration())
			.withLock(lock)
			.withRenewDeadline(properties.renewDeadline())
			.withRetryPeriod(properties.retryPeriod())
			.withLeaderCallbacks(fabric8LeaderElectionCallbacks)
			.build();
	}

	@Bean
	@ConditionalOnMissingBean
	Lock lock(KubernetesClient fabric8KubernetesClient, LeaderElectionProperties properties, String candidateIdentity) {
		boolean leaseSupported = fabric8KubernetesClient.getApiGroups()
			.getGroups()
			.stream()
			.flatMap(x -> x.getVersions().stream())
			.map(GroupVersionForDiscovery::getGroupVersion)
			.filter(COORDINATION_VERSION_GROUP::equals)
			.findFirst()
			.map(fabric8KubernetesClient::getApiResources)
			.map(APIResourceList::getResources)
			.map(x -> x.stream().map(APIResource::getKind))
			.flatMap(x -> x.filter(y -> y.equals(LEASE)).findFirst())
			.isPresent();

		if (leaseSupported) {
			if (properties.useConfigMapAsLock()) {
				LOG.info(() -> "leases are supported on the cluster, but config map will be used "
						+ "(because 'spring.cloud.kubernetes.leader.election.use-config-map-as-lock=true')");
				return new ConfigMapLock(properties.lockNamespace(), properties.lockName(), candidateIdentity);
			}
			else {
				LOG.info(() -> "will use lease as the lock for leader election");
				return new LeaseLock(properties.lockNamespace(), properties.lockName(), candidateIdentity);
			}
		}
		else {
			LOG.info(() -> "will use configmap as the lock for leader election");
			return new ConfigMapLock(properties.lockNamespace(), properties.lockName(), candidateIdentity);
		}
	}

}
