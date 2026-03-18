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

package org.springframework.cloud.kubernetes.client.loadbalancer.it;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServiceListBuilder;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServicePortBuilder;
import io.kubernetes.client.openapi.models.V1ServiceSpecBuilder;
import org.assertj.core.util.Strings;

import static java.util.stream.Collectors.toList;

public final class LoadBalancerMocks {

	private LoadBalancerMocks() {

	}

	/**
	 * when "metadata.name" is requested for all namespaces.
	 */
	public static void mockLoadBalancerServiceCallInAllNamespacesByName(String namespace, String serviceId,
			WireMockServer server) {

		V1Service service = new V1ServiceBuilder()
			.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(server.port(), "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		V1ServiceList serviceList = new V1ServiceListBuilder().withItems(service).build();

		server.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/services?fieldSelector=metadata.name%3D" + serviceId))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));

	}

	/**
	 * mock loadbalancer calls that are made when services are requested in a certain
	 * namespace and the call happens by name ( "metadata.name" ).
	 */
	public static void mockLoadBalancerServiceCallWithFieldMetadataName(String namespace, String serviceId,
			WireMockServer server, int port) {

		V1Service service = new V1ServiceBuilder()
			.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(port, "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(Map.of())
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		V1ServiceList serviceList = new V1ServiceListBuilder().withItems(service).build();

		server.stubFor(WireMock
			.get(WireMock
				.urlEqualTo("/api/v1/namespaces/" + namespace + "/services?fieldSelector=metadata.name%3D" + serviceId))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));
	}

	public static void mockLoadBalancerServiceCallByLabels(String namespace, String serviceId,
			Map<String, String> labels, WireMockServer server, int port) {

		V1Service service = new V1ServiceBuilder()
			.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(port, "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(labels)
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		V1ServiceList serviceList = new V1ServiceListBuilder().withItems(service).build();

		String urlPath = "/api/v1/namespaces/" + namespace + "/services?labelSelector=" + labelSelector(labels);

		server.stubFor(WireMock.get(WireMock.urlEqualTo(urlPath))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));
	}

	/**
	 * when "metadata.labels" is requested for all namespaces.
	 */
	public static void mockLoadBalancerServiceCallInAllNamespacesByLabels(String namespace, String serviceId,
			Map<String, String> serviceLabels, WireMockServer server) {

		V1Service service = new V1ServiceBuilder()
			.withSpec(new V1ServiceSpecBuilder().withType("ClusterIP")
				.withPorts(getServicePorts(Map.of(server.port(), "http")))
				.build())
			.withNewMetadata()
			.withNamespace(namespace)
			.withName(serviceId)
			.withLabels(serviceLabels)
			.withAnnotations(Map.of())
			.endMetadata()
			.build();

		V1ServiceList serviceList = new V1ServiceListBuilder().withItems(service).build();
		String url = "/api/v1/services?labelSelector=" + labelSelector(serviceLabels);

		server.stubFor(WireMock.get(WireMock.urlEqualTo(url))
			.willReturn(WireMock.aResponse().withBody(JSON.serialize(serviceList)).withStatus(200)));
	}

	private static List<V1ServicePort> getServicePorts(Map<Integer, String> ports) {
		return ports.entrySet().stream().map(e -> {
			V1ServicePortBuilder servicePortBuilder = new V1ServicePortBuilder();
			servicePortBuilder.withPort(e.getKey());
			if (!Strings.isNullOrEmpty(e.getValue())) {
				servicePortBuilder.withName(e.getValue());
			}
			return servicePortBuilder.build();
		}).collect(toList());
	}

	private static String labelSelector(Map<String, String> labels) {
		return labels.entrySet()
			.stream()
			.map(en -> en.getKey() + "%3D" + en.getValue())
			.collect(Collectors.joining(","));
	}

}
