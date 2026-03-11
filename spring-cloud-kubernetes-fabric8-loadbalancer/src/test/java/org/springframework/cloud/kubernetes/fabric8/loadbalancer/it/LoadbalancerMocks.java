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

package org.springframework.cloud.kubernetes.fabric8.loadbalancer.it;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.assertj.core.util.Strings;

import static java.util.stream.Collectors.toList;

/**
 * Mock methods that are part of the discovery client indexers initialization.
 *
 * @author wind57
 */
public final class LoadbalancerMocks {

	private LoadbalancerMocks() {

	}

	/**
	 * mock loadbalancer calls that are made when services are requested in a certain
	 * namespace and the call happens by name ( "metadata.name" ).
	 */
	public static void mockLoadBalancerServiceCallWithFieldMetadataName(String namespace, String serviceId,
		KubernetesMockServer kubernetesMockServer, int port,  int numberOfCalls) {

		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(port, "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		ServiceList serviceList = new ServiceListBuilder().withItems(service).build();

		String urlPath = "/api/v1/namespaces/" + namespace + "/services?fieldSelector=metadata.name%3D" + serviceId;

		// mock the list supplier
		kubernetesMockServer.expect().get().withPath(urlPath).andReturn(200, serviceList).times(numberOfCalls);
	}

	/**
	 * when "metadata.name" is requested for all namespaces.
	 */
	public static void mockLoadBalancerServiceCallInAllNamespacesByName(String namespace, String serviceId,
		KubernetesMockServer kubernetesMockServer, int numberOfCalls) {

		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(kubernetesMockServer.getPort(), "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		ServiceList serviceList = new ServiceListBuilder().withItems(service).build();

		// mock the list supplier
		kubernetesMockServer.expect()
			.get()
			.withPath("/api/v1/services?fieldSelector=metadata.name%3D" + serviceId)
			.andReturn(200, serviceList)
			.times(numberOfCalls);
	}

	/**
	 * when "metadata.labels" is requested for all namespaces.
	 */
	public static void mockLoadBalancerServiceCallInAllNamespacesByLabels(String namespace, String serviceId,
		Map<String, String> serviceLabels, KubernetesMockServer kubernetesMockServer, int numberOfCalls) {

		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(kubernetesMockServer.getPort(), "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(serviceLabels)
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		ServiceList serviceList = new ServiceListBuilder().withItems(service).build();
		String url = "/api/v1/services?labelSelector=" + labelSelector(serviceLabels);

		// mock the list supplier
		kubernetesMockServer.expect()
			.get()
			.withPath(url)
			.andReturn(200, serviceList)
			.times(numberOfCalls);
	}

	public static void mockLoadBalancerServiceCallByLabels(String namespace, String serviceId, Map<String, String> labels,
		KubernetesMockServer kubernetesMockServer, int port, int numberOfCalls) {

		Service service = new ServiceBuilder()
			.withSpec(new ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(port, "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(labels)
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		ServiceList serviceList = new ServiceListBuilder().withItems(service).build();

		String urlPath = "/api/v1/namespaces/" + namespace + "/services?labelSelector="  + labelSelector(labels);

		// mock the list supplier
		kubernetesMockServer.expect().get().withPath(urlPath).andReturn(200, serviceList).times(numberOfCalls);
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

	private static String labelSelector(Map<String, String> labels) {
		return labels.entrySet().stream().map(en -> en.getKey() + "%3D" + en.getValue()).collect(Collectors.joining(","));
	}

}
