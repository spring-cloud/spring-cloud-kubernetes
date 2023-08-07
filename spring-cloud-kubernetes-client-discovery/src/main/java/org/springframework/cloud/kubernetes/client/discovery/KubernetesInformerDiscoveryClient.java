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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadataForServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortNameAndNumber;
import org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver;
import org.springframework.core.log.LogAccessor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.filter;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.matchesServiceLabels;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.postConstruct;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.serviceMetadata;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.endpointsPort;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.serviceInstance;
import static org.springframework.cloud.kubernetes.commons.discovery.DiscoveryClientUtils.serviceInstanceMetadata;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.EXTERNAL_NAME;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTP;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.HTTPS;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.PRIMARY_PORT_NAME_LABEL_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SECURED;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.UNSET_PORT_NAME;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.endpointSubsetPortsData;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.portsData;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.serviceMetadata;
import static org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClientUtils.addresses;

import static org.springframework.cloud.kubernetes.client.discovery.K8sInstanceIdHostPodNameSupplier.nonExternalName;
import static org.springframework.cloud.kubernetes.client.discovery.K8sInstanceIdHostPodNameSupplier.externalName;
import static org.springframework.cloud.kubernetes.client.discovery.K8sPodLabelsAndAnnotationsSupplier.nonExternalName;
import static org.springframework.cloud.kubernetes.client.discovery.K8sPodLabelsAndAnnotationsSupplier.externalName;

/**
 * @author Min Kim
 * @author Ryan Baxter
 * @author Tim Yysewyn
 */
public class KubernetesInformerDiscoveryClient implements DiscoveryClient {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesInformerDiscoveryClient.class));

	private final List<SharedInformerFactory> sharedInformerFactories;

	private final List<Lister<V1Service>> serviceListers;

	private final List<Lister<V1Endpoints>> endpointsListers;

	private final Supplier<Boolean> informersReadyFunc;

	private final KubernetesDiscoveryProperties properties;

	private final Predicate<V1Service> filter;

	private final ServicePortSecureResolver servicePortSecureResolver;

	private final CoreV1Api coreV1Api;

	@Deprecated(forRemoval = true)
	public KubernetesInformerDiscoveryClient(String namespace, SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		this.sharedInformerFactories = List.of(sharedInformerFactory);
		this.serviceListers = List.of(serviceLister);
		this.endpointsListers = List.of(endpointsLister);
		this.informersReadyFunc = () -> serviceInformer.hasSynced() && endpointsInformer.hasSynced();
		this.properties = properties;
		filter = filter(properties);
		servicePortSecureResolver = new ServicePortSecureResolver(properties);
		coreV1Api = new CoreV1Api();
	}

	public KubernetesInformerDiscoveryClient(SharedInformerFactory sharedInformerFactory,
			Lister<V1Service> serviceLister, Lister<V1Endpoints> endpointsLister,
			SharedInformer<V1Service> serviceInformer, SharedInformer<V1Endpoints> endpointsInformer,
			KubernetesDiscoveryProperties properties) {
		this.sharedInformerFactories = List.of(sharedInformerFactory);
		this.serviceListers = List.of(serviceLister);
		this.endpointsListers = List.of(endpointsLister);
		this.informersReadyFunc = () -> serviceInformer.hasSynced() && endpointsInformer.hasSynced();
		this.properties = properties;
		filter = filter(properties);
		servicePortSecureResolver = new ServicePortSecureResolver(properties);
		coreV1Api = new CoreV1Api();
	}

	public KubernetesInformerDiscoveryClient(List<SharedInformerFactory> sharedInformerFactories,
			List<Lister<V1Service>> serviceListers, List<Lister<V1Endpoints>> endpointsListers,
			List<SharedInformer<V1Service>> serviceInformers, List<SharedInformer<V1Endpoints>> endpointsInformers,
			KubernetesDiscoveryProperties properties) {
		this.sharedInformerFactories = sharedInformerFactories;

		this.serviceListers = serviceListers;
		this.endpointsListers = endpointsListers;
		this.informersReadyFunc = () -> {
			boolean serviceInformersReady = serviceInformers.isEmpty() || serviceInformers.stream()
					.map(SharedInformer::hasSynced).reduce(Boolean::logicalAnd).orElse(false);
			boolean endpointsInformersReady = endpointsInformers.isEmpty() || endpointsInformers.stream()
					.map(SharedInformer::hasSynced).reduce(Boolean::logicalAnd).orElse(false);
			return serviceInformersReady && endpointsInformersReady;
		};

		this.properties = properties;
		filter = filter(properties);
		servicePortSecureResolver = new ServicePortSecureResolver(properties);
		coreV1Api = new CoreV1Api();
	}

	@Override
	public String description() {
		return "Kubernetes Client Discovery";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId must be provided");

		List<V1Service> services = serviceListers.stream().flatMap(x -> x.list().stream())
				.filter(scv -> scv.getMetadata() != null).filter(svc -> serviceId.equals(svc.getMetadata().getName()))
				.filter(scv -> matchesServiceLabels(scv, properties)).filter(filter).toList();
		return services.stream().flatMap(service -> getServiceInstanceDetails(service, serviceId)).toList();
	}

	private Stream<ServiceInstance> getServiceInstanceDetails(V1Service service, String serviceId) {
		List<V1Endpoints> endpoints = endpointsListers.stream()
				.map(endpointsLister -> endpointsLister.namespace(service.getMetadata().getNamespace())
						.get(service.getMetadata().getName()))
				.filter(Objects::nonNull).filter(ep -> ep.getSubsets() != null).toList();

		List<ServiceInstance> instances = new ArrayList<>();
		for (V1Endpoints es : endpoints) {
			// subsetsNS are only those that matched the serviceId
			instances.addAll(serviceInstances(service, es, serviceId));
		}

		if (properties.includeExternalNameServices()) {
			LOG.debug(() -> "Searching for 'ExternalName' type of services with serviceId : " + serviceId);
			serviceListers.stream().flatMap(lister -> lister.list().stream())






			List<V1Service> services = services(properties, client, namespaceProvider,
				s -> s.getSpec().getType().equals(EXTERNAL_NAME), Map.of("metadata.name", serviceId),
				"fabric8-discovery");
			for (Service service : services) {
				ServiceMetadata serviceMetadata = serviceMetadata(service);
				Map<String, String> serviceInstanceMetadata = serviceInstanceMetadata(Map.of(), serviceMetadata,
					properties);

				Fabric8InstanceIdHostPodNameSupplier supplierOne = externalName(service);
				Fabric8PodLabelsAndAnnotationsSupplier supplierTwo = externalName();

				ServiceInstance externalNameServiceInstance = serviceInstance(null, serviceMetadata, supplierOne,
					supplierTwo, new ServicePortNameAndNumber(-1, null), serviceInstanceMetadata, properties);

				instances.add(externalNameServiceInstance);
			}
		}

		return instances;

	}

	private List<ServiceInstance> serviceInstances(V1Service service, V1Endpoints es, String serviceId) {

		List<V1EndpointSubset> subsets = es.getSubsets();
		if (subsets == null || subsets.isEmpty()) {
			LOG.debug(() -> "serviceId : " + serviceId + " does not have any subsets");
			return List.of();
		}

		String namespace = es.getMetadata().getNamespace();
		List<ServiceInstance> instances = new ArrayList<>();

		ServiceMetadata serviceMetadata = serviceMetadata(service);
		Map<String, String> portsData = portsData(subsets);
		Map<String, String> serviceInstanceMetadata = serviceInstanceMetadata(portsData, serviceMetadata, properties);

		for (V1EndpointSubset endpointSubset : subsets) {

			LinkedHashMap<String, Integer> endpointsPortData = endpointSubsetPortsData(endpointSubset);
			ServicePortNameAndNumber portData = endpointsPort(endpointsPortData, serviceMetadata, properties);
			List<V1EndpointAddress> addresses = addresses(endpointSubset, properties);

			for (V1EndpointAddress endpointAddress : addresses) {

				K8sInstanceIdHostPodNameSupplier supplierOne = nonExternalName(endpointAddress, service);
				K8sPodLabelsAndAnnotationsSupplier supplierTwo = nonExternalName(coreV1Api, namespace);

				ServiceInstance serviceInstance = serviceInstance(servicePortSecureResolver, serviceMetadata,
					supplierOne, supplierTwo, portData, serviceInstanceMetadata, properties);
				instances.add(serviceInstance);
			}
		}

		return instances;
	}


	@Override
	public List<String> getServices() {
		List<String> services = serviceListers.stream().flatMap(serviceLister -> serviceLister.list().stream())
				.filter(service -> matchesServiceLabels(service, properties)).filter(filter)
				.map(s -> s.getMetadata().getName()).distinct().toList();
		LOG.debug(() -> "will return services : " + services);
		return services;
	}

	@PostConstruct
	public void afterPropertiesSet() {
		postConstruct(sharedInformerFactories, properties, informersReadyFunc, serviceListers);
	}

	@Override
	public int getOrder() {
		return properties.order();
	}

}
