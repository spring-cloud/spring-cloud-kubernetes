/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClientUtils.addresses;
import static org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClientUtils.endpoints;
import static org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClientUtils.endpointsPort;
import static org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClientUtils.serviceMetadata;

/**
 * Kubernetes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 * @author Tim Ysewyn
 */
public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesDiscoveryClient.class));

	private final KubernetesDiscoveryProperties properties;

	private final KubernetesClientServicesFunction kubernetesClientServicesFunction;

	private final ServicePortSecureResolver servicePortSecureResolver;

	private final Fabric8DiscoveryServicesAdapter adapter;

	private KubernetesClient client;

	public KubernetesDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction) {

		this(client, kubernetesDiscoveryProperties, kubernetesClientServicesFunction, null,
				new ServicePortSecureResolver(kubernetesDiscoveryProperties));
	}

	KubernetesDiscoveryClient(KubernetesClient client, KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction, Predicate<Service> filter,
			ServicePortSecureResolver servicePortSecureResolver) {

		this.client = client;
		this.properties = kubernetesDiscoveryProperties;
		this.servicePortSecureResolver = servicePortSecureResolver;
		this.kubernetesClientServicesFunction = kubernetesClientServicesFunction;
		this.adapter = new Fabric8DiscoveryServicesAdapter(kubernetesClientServicesFunction,
				kubernetesDiscoveryProperties, filter);
	}

	public KubernetesClient getClient() {
		return this.client;
	}

	public void setClient(KubernetesClient client) {
		this.client = client;
	}

	@Override
	public String description() {
		return "Fabric8 Kubernetes Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId);

		List<EndpointSubsetNS> subsetsNS = getEndPointsList(serviceId).stream()
				.map(KubernetesDiscoveryClientUtils::subsetsFromEndpoints).toList();

		List<ServiceInstance> instances = new ArrayList<>();
		for (EndpointSubsetNS es : subsetsNS) {
			instances.addAll(getNamespaceServiceInstances(es, serviceId));
		}

		return instances;
	}

	public List<Endpoints> getEndPointsList(String serviceId) {
		if (properties.allNamespaces()) {
			LOG.debug(() -> "searching for endpoints in all namespaces");
			return endpoints(client.endpoints().inAnyNamespace().withNewFilter(), properties, serviceId);
		}
		else if (properties.namespaces().isEmpty()) {
			LOG.debug(() -> "searching for endpoints in namespace : " + client.getNamespace());
			return endpoints(client.endpoints().withNewFilter(), properties, serviceId);
		}
		else {
			LOG.debug(() -> "searching for endpoints in namespaces : " + properties.namespaces());
			List<Endpoints> endpoints = new ArrayList<>();
			for (String namespace : properties.namespaces()) {
				endpoints.addAll(
						endpoints(client.endpoints().inNamespace(namespace).withNewFilter(), properties, serviceId));
			}
			return endpoints;
		}
	}

	private List<ServiceInstance> getNamespaceServiceInstances(EndpointSubsetNS es, String serviceId) {

		List<EndpointSubset> subsets = es.endpointSubset();
		if (subsets.isEmpty()) {
			LOG.debug(() -> "serviceId : " + serviceId + " does not have any subsets");
			return List.of();
		}

		String namespace = es.namespace();
		List<ServiceInstance> instances = new ArrayList<>();

		Service service = client.services().inNamespace(namespace).withName(serviceId).get();
		Map<String, String> serviceMetadata = serviceMetadata(serviceId, service, properties, subsets, namespace);

		for (EndpointSubset endpointSubset : subsets) {
			int endpointPort = endpointsPort(endpointSubset, serviceId, properties, service);
			List<EndpointAddress> addresses = addresses(endpointSubset, properties);
			for (EndpointAddress endpointAddress : addresses) {

				String instanceId = null;
				if (endpointAddress.getTargetRef() != null) {
					instanceId = endpointAddress.getTargetRef().getUid();
				}
				instances
						.add(new DefaultKubernetesServiceInstance(instanceId, serviceId, endpointAddress.getIp(),
								endpointPort, serviceMetadata,
								servicePortSecureResolver.resolve(new ServicePortSecureResolver.Input(endpointPort,
										service.getMetadata().getName(), service.getMetadata().getLabels(),
										service.getMetadata().getAnnotations()))));
			}
		}

		return instances;
	}

	@Override
	public List<String> getServices() {
		return adapter.apply(client).stream().map(s -> s.getMetadata().getName()).toList();
	}

	@Deprecated(forRemoval = true)
	public List<String> getServices(Predicate<Service> filter) {
		return new Fabric8DiscoveryServicesAdapter(kubernetesClientServicesFunction, properties, filter).apply(client)
				.stream().map(s -> s.getMetadata().getName()).toList();
	}

	@Override
	public int getOrder() {
		return properties.order();
	}

}
