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

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Lister;

import org.assertj.core.util.Strings;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static java.util.stream.Collectors.toList;

/**
 * @author wind57
 */
abstract class Fabric8DiscoveryClientBase {

	private final Fabric8InformerAutoConfiguration configuration = new Fabric8InformerAutoConfiguration();

	Fabric8DiscoveryClient fabric8DiscoveryClient(KubernetesDiscoveryProperties properties,
			List<String> selectiveNamespaces, KubernetesClient kubernetesClient) {
		List<SharedIndexInformer<Service>> serviceInformers =  configuration.serviceSharedIndexInformers(
			selectiveNamespaces, kubernetesClient, properties);
		List<SharedIndexInformer<Endpoints>> endpointsInformers =  configuration.endpointsSharedIndexInformers(
			selectiveNamespaces, kubernetesClient, properties);
		List<Lister<Service>> serviceListers =  configuration.serviceListers(selectiveNamespaces, serviceInformers);
		List<Lister<Endpoints>> endpointsListers =  configuration.endpointsListers(selectiveNamespaces, endpointsInformers);

		return new Fabric8DiscoveryClient(kubernetesClient, serviceListers,
			endpointsListers, serviceInformers, endpointsInformers, properties,
			new Fabric8DiscoveryClientSpelAutoConfiguration().predicate(properties));
	}

	void setupServiceWithLabelsAndAnnotationsAndPorts(KubernetesClient kubernetesClient, String serviceId, String namespace,
		Map<String, String> labels, Map<String, String> annotations, Map<Integer, String> ports) {

		Service service = service(serviceId, namespace, labels, annotations, ports);
		kubernetesClient.services().inNamespace(namespace).resource(service).create();

		Endpoints endpoints = endpoints(namespace, serviceId, labels, ports);
		kubernetesClient.endpoints().inNamespace(namespace).resource(endpoints).create();

	}

	static Service service(String namespace, String serviceId, Map<String, String> labels,
			Map<String, String> annotations, Map<Integer, String> ports) {
		return new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP").withPorts(getServicePorts(ports)).build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(labels)
			.withAnnotations(annotations)
			.endMetadata()
			.build();
	}

	static Endpoints endpoints(String namespace, String serviceId, Map<String, String> labels, Map<Integer, String> ports) {

		ObjectMeta objectMeta = new ObjectMeta();
		objectMeta.setNamespace(namespace);
		objectMeta.setName(serviceId);
		objectMeta.setLabels(labels);

		return new EndpointsBuilder().withMetadata(objectMeta)
			.addNewSubset()
			.addAllToPorts(getEndpointPorts(ports))
			.addNewAddress()
			.endAddress()
			.endSubset()
			.build();
	}

	private static List<ServicePort> getServicePorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			ServicePortBuilder servicePortBuilder = new ServicePortBuilder();
			servicePortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				servicePortBuilder.withName(e.getValue());
			}
			return servicePortBuilder.build();
		}).collect(toList());
	}

	private static List<EndpointPort> getEndpointPorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			EndpointPortBuilder endpointPortBuilder = new EndpointPortBuilder();
			endpointPortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				endpointPortBuilder.withName(e.getValue());
			}
			return endpointPortBuilder.build();
		}).collect(toList());
	}

}
