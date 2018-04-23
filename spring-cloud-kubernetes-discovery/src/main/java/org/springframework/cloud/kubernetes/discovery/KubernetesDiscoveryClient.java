/*
 *   Copyright (C) 2016 to the original authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.springframework.cloud.kubernetes.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.util.Assert;

public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClient.class);
	private static final String HOSTNAME = "HOSTNAME";

	private KubernetesClient client;
	private KubernetesDiscoveryProperties properties;

	public KubernetesDiscoveryClient(KubernetesClient client,
									 KubernetesDiscoveryProperties kubernetesDiscoveryProperties) {
		this.client = client;
		this.properties = properties;
	}

	public KubernetesClient getClient() {
		return client;
	}

	public void setClient(KubernetesClient client) {
		this.client = client;
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

	public ServiceInstance getLocalServiceInstance() {
		String serviceName = properties.getServiceName();
		String podName = System.getenv(HOSTNAME);
		ServiceInstance defaultInstance = new DefaultServiceInstance(serviceName,
																	 "localhost",
																	 8080,
																	 false);

		Endpoints endpoints = client.endpoints().withName(serviceName).get();
		Optional<Service> service = Optional.ofNullable(client.services().withName(serviceName).get());
		final Map<String, String> labels;
		if (service.isPresent()) {
			labels = service.get().getMetadata().getLabels();
		} else {
			labels = null;
		}
		if (Utils.isNullOrEmpty(podName) || endpoints == null) {
			return defaultInstance;
		}
		try {
			List<EndpointSubset> subsets = endpoints.getSubsets();

			if (subsets != null) {
				for (EndpointSubset s : subsets) {
					List<EndpointAddress> addresses = s.getAddresses();
					for (EndpointAddress a : addresses) {
						return new KubernetesServiceInstance(serviceName,
																	a,
																	s.getPorts().stream().findFirst().orElseThrow(IllegalStateException::new),
																	labels,
																	false);
					}
				}
			}
			return defaultInstance;

		} catch (Throwable t) {
			return defaultInstance;
		}
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId,
					   "[Assertion failed] - the object argument must be null");
		Optional<Service> service = Optional.ofNullable(client.services().withName(serviceId).get());
		final Map<String, String> labels;
		if (service.isPresent()) {
			labels = service.get().getMetadata().getLabels();
		} else {
			labels = null;
		}

		Optional<Endpoints> endpoints = Optional.ofNullable(client.endpoints().withName(serviceId).get());
		List<EndpointSubset> subsets = endpoints.get().getSubsets();
		List<ServiceInstance> instances = new ArrayList<>();
		if (subsets != null) {
			for (EndpointSubset s : subsets) {
				List<EndpointAddress> addresses = s.getAddresses();
				for (EndpointAddress a : addresses) {
					instances.add(new KubernetesServiceInstance(serviceId,
																a,
																s.getPorts().stream().findFirst().orElseThrow(IllegalStateException::new),
																labels,
																false));
				}
			}
		}

		return instances;
	}

	@Override
	public List<String> getServices() {
		return client.services().list()
			.getItems()
			.stream().map(s -> s.getMetadata().getName())
			.collect(Collectors.toList());
	}
}
