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

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.Lock;
import io.kubernetes.client.extended.leaderelection.resourcelock.ConfigMapLock;
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;

import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.commons.leader.election.ConditionalOnLeaderElectionEnabled;
import org.springframework.cloud.kubernetes.commons.leader.election.LeaderElectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.COORDINATION_GROUP;
import static org.springframework.cloud.kubernetes.commons.leader.LeaderUtils.COORDINATION_VERSION;

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

	private static final LogAccessor LOG = new LogAccessor(KubernetesClientLeaderElectionAutoConfiguration.class);

	@Bean
	@ConditionalOnClass(InfoContributor.class)
	@ConditionalOnEnabledInfoContributor("leader.election")
	KubernetesClientLeaderElectionInfoContributor kubernetesClientLeaderElectionInfoContributor(
			String candidateIdentity, LeaderElectionConfig leaderElectionConfig) {
		return new KubernetesClientLeaderElectionInfoContributor(candidateIdentity, leaderElectionConfig);
	}

	@Bean
	@ConditionalOnMissingBean
	KubernetesClientLeaderElectionInitiator kubernetesClientLeaderElectionInitiator(String candidateIdentity,
			String podNamespace, LeaderElectionConfig leaderElectionConfig,
			LeaderElectionProperties leaderElectionProperties, BooleanSupplier podReadySupplier,
			KubernetesClientLeaderElectionCallbacks callbacks) {
		return new KubernetesClientLeaderElectionInitiator(candidateIdentity, podNamespace, leaderElectionConfig,
				leaderElectionProperties, podReadySupplier, callbacks);
	}

	@Bean
	@ConditionalOnMissingBean
	BooleanSupplier kubernetesClientPodReadySupplier(CoreV1Api coreV1Api, String candidateIdentity,
			String podNamespace) {
		return () -> {
			try {
				V1Pod pod = coreV1Api.readNamespacedPod(candidateIdentity, podNamespace).execute();
				return isPodReady(pod);
			}
			catch (ApiException e) {
				throw new RuntimeException(e);
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean
	LeaderElectionConfig kubernetesClientLeaderElectionConfig(LeaderElectionProperties properties, Lock lock) {
		return new LeaderElectionConfig(lock, properties.leaseDuration(), properties.renewDeadline(),
				properties.retryPeriod());
	}

	@Bean
	@ConditionalOnMissingBean
	Lock kubernetesClientLeaderElectionLock(ApiClient apiClient, LeaderElectionProperties properties,
			String candidateIdentity) {

		CustomObjectsApi customObjectsApi = new CustomObjectsApi(apiClient);
		boolean leaseSupported;
		try {
			List<V1APIResource> resources = customObjectsApi.getAPIResources(COORDINATION_GROUP, COORDINATION_VERSION)
				.execute()
				.getResources();

			leaseSupported = resources.stream().map(V1APIResource::getKind).anyMatch("Lease"::equals);
		}
		catch (ApiException e) {
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

	// following two methods are a verbatim copy of the fabric8 implementation
	private static boolean isPodReady(V1Pod pod) {
		Objects.requireNonNull(pod, "Pod can't be null.");
		V1PodCondition condition = getPodReadyCondition(pod);

		if (condition == null) {
			return false;
		}
		return condition.getStatus().equalsIgnoreCase("True");
	}

	private static V1PodCondition getPodReadyCondition(V1Pod pod) {
		if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
			return null;
		}

		for (V1PodCondition condition : pod.getStatus().getConditions()) {
			if ("Ready".equals(condition.getType())) {
				return condition;
			}
		}
		return null;
	}

}
