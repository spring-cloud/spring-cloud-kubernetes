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

import io.kubernetes.client.extended.leaderelection.Lock;
import io.kubernetes.client.extended.leaderelection.resourcelock.ConfigMapLock;
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1Pod;
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

import java.util.List;
import java.util.function.BooleanSupplier;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.DISCOVERY_GROUP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.DISCOVERY_VERSION;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.ENDPOINT_SLICE;
import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.COORDINATION_GROUP;
import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.COORDINATION_VERSION;
import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.LEASE;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LeaderElectionProperties.class)
@ConditionalOnBean(ApiClient.class)
@ConditionalOnLeaderElectionEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureAfter(KubernetesClientLeaderElectionCallbacksAutoConfiguration.class)
class KubernetesClientLeaderElectionAutoConfiguration {

	private static final String COORDINATION_VERSION_GROUP = COORDINATION_GROUP + "/" + COORDINATION_VERSION;

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientLeaderElectionAutoConfiguration.class);

//	@Bean
//	@ConditionalOnClass(InfoContributor.class)
//	@ConditionalOnEnabledHealthIndicator("leader.election")
//	Fabric8LeaderElectionInfoContributor leaderElectionInfoContributor(String candidateIdentity,
//		LeaderElectionConfig leaderElectionConfig, KubernetesClient fabric8KubernetesClient) {
//		return new Fabric8LeaderElectionInfoContributor(candidateIdentity, leaderElectionConfig,
//			fabric8KubernetesClient);
//	}
//
//	@Bean
//	@ConditionalOnMissingBean
//	Fabric8LeaderElectionInitiator fabric8LeaderElectionInitiator(String candidateIdentity, String podNamespace,
//		KubernetesClient fabric8KubernetesClient, LeaderElectionConfig fabric8LeaderElectionConfig,
//		LeaderElectionProperties leaderElectionProperties, BooleanSupplier podReadySupplier) {
//		return new Fabric8LeaderElectionInitiator(candidateIdentity, podNamespace, fabric8KubernetesClient,
//			fabric8LeaderElectionConfig, leaderElectionProperties, podReadySupplier);
//	}
//
	@Bean
	BooleanSupplier podReadySupplier(CoreV1Api coreV1Api, String candidateIdentity,
		String podNamespace) {
		return () -> {
			try {
				V1Pod pod = coreV1Api.readNamespacedPod(candidateIdentity, podNamespace).execute();
				return isPodReady(pod);
			} catch (ApiException e) {
				throw new RuntimeException(e);
			}
		};
	}
//
//	@Bean
//	@ConditionalOnMissingBean
//	LeaderElectionConfig fabric8LeaderElectionConfig(LeaderElectionProperties properties, Lock lock,
//		Fabric8LeaderElectionCallbacks fabric8LeaderElectionCallbacks) {
//		return new LeaderElectionConfigBuilder().withReleaseOnCancel()
//			.withName("Spring k8s leader election")
//			.withLeaseDuration(properties.leaseDuration())
//			.withLock(lock)
//			.withRenewDeadline(properties.renewDeadline())
//			.withRetryPeriod(properties.retryPeriod())
//			.withLeaderCallbacks(fabric8LeaderElectionCallbacks)
//			.build();
//	}

	@Bean
	@ConditionalOnMissingBean
	Lock lock(ApiClient apiClient, LeaderElectionProperties properties, String candidateIdentity) {

		CustomObjectsApi customObjectsApi = new CustomObjectsApi(apiClient);
		boolean leaseSupported = false;
		try {
			List<V1APIResource> resources = customObjectsApi.getAPIResources(COORDINATION_GROUP, COORDINATION_VERSION)
				.execute()
				.getResources();

			leaseSupported = resources.stream().map(V1APIResource::getKind).anyMatch("Lease"::equals);
		} catch (ApiException e) {
			throw new RuntimeException(e);
		}

		if (leaseSupported) {
			if (properties.useConfigMapAsLock()) {
				LOG.info(() -> "leases are supported on the cluster, but config map will be used "
					+ "(because 'spring.cloud.kubernetes.leader.election.use-config-map-as-lock=true')");
				return new ConfigMapLock(properties.lockNamespace(), properties.lockName(), candidateIdentity);
			}
			else {
				LOG.info(() -> "will use lease as the lock for leader election");
				return new LeaseLock(properties.lockNamespace(), properties.lockName(), candidateIdentity, apiClient);
			}
		}
		else {
			LOG.info(() -> "will use configmap as the lock for leader election");
			return new ConfigMapLock(properties.lockNamespace(), properties.lockName(), candidateIdentity, apiClient);
		}
	}

	private boolean isPodReady(V1Pod pod) {
		return pod != null
			&& pod.getStatus() != null
			&& pod.getStatus().getConditions() != null
			&& pod.getStatus().getConditions().stream()
			.anyMatch(c ->
				"Ready".equals(c.getType()) && "True".equals(c.getStatus())
			);
	}

}
