/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.discovery;

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClient.class);

	private final KubernetesClient kubernetesClient;

	public KubernetesDiscoveryClient(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId,
			"[Assertion failed] - the object argument must be null");
		return getServiceInstances(serviceId);
	}

	@Override
	public List<String> getServices() {
		return kubernetesClient.services().list().getItems().stream()
			.map(s -> s.getMetadata().getName())
			.collect(Collectors.toList());
	}

	private List<ServiceInstance> getServiceInstances(String serviceId) {
		List<ServiceInstance> instances = new ArrayList<>();
		try {
			Optional<Service> service = Optional.ofNullable(kubernetesClient.services().withName(serviceId).get());
			Map<String, String> labels = service.isPresent() ? service.get().getMetadata().getLabels() : null;

			Endpoints endpoints = kubernetesClient.endpoints().withName(serviceId).get();
			Optional<Endpoints> optionalEndpoints = Optional.ofNullable(endpoints);

			if (optionalEndpoints.isPresent()) {
				List<EndpointSubset> endpointSubsets = optionalEndpoints.get().getSubsets();
				for (EndpointSubset endpointSubset : endpointSubsets) {
					Optional<EndpointPort> optionalEndpointPort = endpointSubset.getPorts().stream()
						.findFirst();
					if (optionalEndpointPort.isPresent()) {
						instances.addAll(endpointSubset.getAddresses().stream()
							.map(endpointAddress -> {
								KubernetesServiceInstance kubernetesServiceInstance = new KubernetesServiceInstance(serviceId,
									endpointAddress,
									optionalEndpointPort.get(),
									labels,
									false);
								return kubernetesServiceInstance;
							})
							.collect(Collectors.toList()));
					}
				}
			}
		} catch (Exception e) {
			log.error("Error calling Kubernetes server", e);
		}
		return instances;
	}
}
