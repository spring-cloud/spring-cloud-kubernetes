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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.poll;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.UNSET_PORT_NAME;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author wind57
 */
final class Fabric8DiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8DiscoveryClientUtils.class));

	static final Predicate<Service> ALWAYS_TRUE = x -> true;

	private Fabric8DiscoveryClientUtils() {

	}

	static void postConstruct(KubernetesDiscoveryProperties properties, Supplier<Boolean> informersReadyFunc,
			List<Lister<Service>> serviceListers) {

		if (!poll(Duration.ofSeconds(1), Duration.ofSeconds(properties.cacheLoadingTimeoutSeconds()), () -> {
			LOG.info(() -> "Waiting for the cache of informers to be fully loaded..");
			return informersReadyFunc.get();
		})) {
			if (properties.waitCacheReady()) {
				throw new IllegalStateException(
						"Timeout waiting for informers cache to be ready, is the kubernetes service up?");
			}
			else {
				LOG.warn(() -> "Timeout waiting for informers cache to be ready, "
						+ "ignoring the failure because waitForInformerCacheReady property is false");
			}
		}
		else {
			LOG.info(() -> "Cache fully loaded (total " + serviceListers.stream().mapToLong(x -> x.list().size()).sum()
					+ " services), discovery client is now available");
		}

	}

	static List<EndpointAddress> addresses(EndpointSubset endpointSubset, KubernetesDiscoveryProperties properties) {
		List<EndpointAddress> addresses = Optional.ofNullable(endpointSubset.getAddresses())
			.map(ArrayList::new)
			.orElse(new ArrayList<>());

		if (properties.includeNotReadyAddresses()) {
			List<EndpointAddress> notReadyAddresses = endpointSubset.getNotReadyAddresses();
			if (CollectionUtils.isEmpty(notReadyAddresses)) {
				return addresses;
			}
			addresses.addAll(notReadyAddresses);
		}

		return addresses;
	}

	/**
	 * a service is allowed to have a single port defined without a name.
	 */
	static Map<String, Integer> endpointSubsetsPortData(List<EndpointSubset> endpointSubsets) {
		return endpointSubsets.stream()
			.flatMap(endpointSubset -> endpointSubset.getPorts().stream())
			.collect(Collectors.toMap(
					endpointPort -> hasText(endpointPort.getName()) ? endpointPort.getName() : UNSET_PORT_NAME,
					EndpointPort::getPort));
	}

	static SharedIndexInformer<Service> serviceSharedIndexInformer(String namespace, KubernetesClient kubernetesClient,
			Map<String, String> serviceLabels) {

		SharedIndexInformer<Service> sharedIndexInformer;

		if (serviceLabels == null) {
			serviceLabels = Map.of();
		}

		// we treat this as all namespaces
		if ("".equals(namespace)) {
			sharedIndexInformer = kubernetesClient.services().inAnyNamespace().withLabels(serviceLabels).inform();
		}
		else {
			sharedIndexInformer = kubernetesClient.services().inNamespace(namespace).withLabels(serviceLabels).inform();
		}

		return sharedIndexInformer;
	}

	static SharedIndexInformer<Endpoints> endpointsSharedIndexInformer(String namespace,
			KubernetesClient kubernetesClient, Map<String, String> serviceLabels) {

		SharedIndexInformer<Endpoints> sharedIndexInformer;

		if (serviceLabels == null) {
			serviceLabels = Map.of();
		}

		// we treat this as all namespaces
		if ("".equals(namespace)) {
			LOG.debug(() -> "discovering endpoints in all namespaces");
			sharedIndexInformer = kubernetesClient.endpoints().inAnyNamespace().withLabels(serviceLabels).inform();
		}
		else {
			LOG.debug(() -> "discovering endpoints in namespace : " + namespace);
			sharedIndexInformer = kubernetesClient.endpoints()
				.inNamespace(namespace)
				.withLabels(serviceLabels)
				.inform();
		}

		return sharedIndexInformer;
	}

}
