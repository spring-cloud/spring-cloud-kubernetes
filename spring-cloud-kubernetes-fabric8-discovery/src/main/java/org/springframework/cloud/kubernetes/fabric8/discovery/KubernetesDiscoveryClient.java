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
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadataForServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.EXTERNAL_NAME;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.addresses;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.endpointSubsetPortsData;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.endpoints;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.portsData;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.serviceInstance;
import static org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8KubernetesDiscoveryClientUtils.services;

/**
 * Fabric8 Kubernetes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 * @author Tim Ysewyn
 */
public class KubernetesDiscoveryClient implements DiscoveryClient, EnvironmentAware {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesDiscoveryClient.class));

	private final KubernetesDiscoveryProperties properties;

	private final KubernetesClientServicesFunction kubernetesClientServicesFunction;

	private final ServicePortSecureResolver servicePortSecureResolver;

	private final Fabric8DiscoveryServicesAdapter adapter;

	private KubernetesClient client;

	private KubernetesNamespaceProvider namespaceProvider;

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
				.map(Fabric8KubernetesDiscoveryClientUtils::subsetsFromEndpoints).toList();

		List<ServiceInstance> instances = new ArrayList<>();
		for (EndpointSubsetNS es : subsetsNS) {
			// subsetsNS are only those that matched the serviceId
			instances.addAll(getNamespaceServiceInstances(es, serviceId));
		}

		if (properties.includeExternalNameServices()) {
			LOG.debug(() -> "Searching for 'ExternalName' type of services with serviceId : " + serviceId);
			List<Service> services = services(properties, client, namespaceProvider,
					s -> s.getSpec().getType().equals(EXTERNAL_NAME), Map.of("metadata.name", serviceId),
					"fabric8-discovery");
			for (Service service : services) {
				ObjectMeta serviceMetadata = service.getMetadata();
				Map<String, String> result = DiscoveryClientUtils.serviceMetadata(serviceId,
						serviceMetadata.getLabels(), serviceMetadata.getAnnotations(), Map.of(), properties,
						serviceMetadata.getNamespace(), service.getSpec().getType());

				ServiceMetadataForServiceInstance forServiceInstance = new ServiceMetadataForServiceInstance(
					service.getMetadata().getName(), service.getMetadata().getLabels(),
					service.getMetadata().getAnnotations()
				);

				ServiceInstance externalNameServiceInstance = serviceInstance(null, forServiceInstance,
					new Fabric8InstanceIdHostPodNameSupplier(null, service),
					new Fabric8PodLabelsAndAnnotationsSupplier(null, null),
					new ServicePortNameAndNumber(-1, null), serviceId, result, service.getMetadata().getNamespace(),
					properties);
				instances.add(externalNameServiceInstance);
			}
		}

		return instances;
	}

	public List<Endpoints> getEndPointsList(String serviceId) {
		return endpoints(properties, client, namespaceProvider, "fabric8-discovery", serviceId, adapter.filter());
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
		ObjectMeta serviceMetadata = service.getMetadata();

		Map<String, String> result = DiscoveryClientUtils.serviceMetadata(serviceId, serviceMetadata.getLabels(),
				serviceMetadata.getAnnotations(), portsData(subsets), properties, serviceMetadata.getNamespace(),
				service.getSpec().getType());

		for (EndpointSubset endpointSubset : subsets) {

			ServicePortNameAndNumber portData = DiscoveryClientUtils.endpointsPort(
					endpointSubsetPortsData(endpointSubset), serviceId, properties, service.getMetadata().getLabels());

			List<EndpointAddress> addresses = addresses(endpointSubset, properties);
			for (EndpointAddress endpointAddress : addresses) {

				ServiceMetadataForServiceInstance forServiceInstance = new ServiceMetadataForServiceInstance(
					service.getMetadata().getName(), service.getMetadata().getLabels(),
					service.getMetadata().getAnnotations()
				);

				ServiceInstance serviceInstance = serviceInstance(servicePortSecureResolver, forServiceInstance,
					new Fabric8InstanceIdHostPodNameSupplier(endpointAddress, service),
					new Fabric8PodLabelsAndAnnotationsSupplier(client, namespace),
					portData, serviceId, result, namespace, properties);
				instances.add(serviceInstance);
			}
		}

		return instances;
	}

	@Override
	public List<String> getServices() {
		return adapter.apply(client).stream().map(s -> s.getMetadata().getName()).distinct().toList();
	}

	@Deprecated(forRemoval = true)
	public List<String> getServices(Predicate<Service> filter) {
		return new Fabric8DiscoveryServicesAdapter(kubernetesClientServicesFunction, properties, filter).apply(client)
				.stream().map(s -> s.getMetadata().getName()).distinct().toList();
	}

	@Override
	public int getOrder() {
		return properties.order();
	}

	@Deprecated(forRemoval = true)
	@Override
	public final void setEnvironment(Environment environment) {
		namespaceProvider = new KubernetesNamespaceProvider(environment);
	}

}
