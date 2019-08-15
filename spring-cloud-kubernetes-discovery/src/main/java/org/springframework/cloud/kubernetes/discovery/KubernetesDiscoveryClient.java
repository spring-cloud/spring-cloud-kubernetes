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

package org.springframework.cloud.kubernetes.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;

/**
 * Kubeneretes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 */
public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClient.class);

	private final KubernetesDiscoveryProperties properties;

	private final DefaultIsServicePortSecureResolver isServicePortSecureResolver;

	private final KubernetesClientServicesFunction kubernetesClientServicesFunction;

	private final SpelExpressionParser parser = new SpelExpressionParser();

	private final SimpleEvaluationContext evalCtxt = SimpleEvaluationContext
			.forReadOnlyDataBinding().withInstanceMethods().build();

	private KubernetesClient client;

	public KubernetesDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction) {

		this(client, kubernetesDiscoveryProperties, kubernetesClientServicesFunction,
				new DefaultIsServicePortSecureResolver(kubernetesDiscoveryProperties));
	}

	KubernetesDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction,
			DefaultIsServicePortSecureResolver isServicePortSecureResolver) {

		this.client = client;
		this.properties = kubernetesDiscoveryProperties;
		this.kubernetesClientServicesFunction = kubernetesClientServicesFunction;
		this.isServicePortSecureResolver = isServicePortSecureResolver;
	}

	public KubernetesClient getClient() {
		return this.client;
	}

	public void setClient(KubernetesClient client) {
		this.client = client;
	}

	@Override
	public String description() {
		return "Kubernetes Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId,
				"[Assertion failed] - the object argument must not be null");
		List<Endpoints> endpointsList = this.properties.isAllNamespaces()
				? this.client.endpoints().inAnyNamespace()
						.withField("metadata.name", serviceId).list().getItems()
				: Collections
						.singletonList(this.client.endpoints().withName(serviceId).get());
		List<EndpointSubset> subsets = endpointsList.stream()
				.flatMap(endpoints -> getSubsetsFromEndpoints(endpoints).stream())
				.collect(Collectors.toList());
		List<ServiceInstance> instances = new ArrayList<>();
		if (!subsets.isEmpty()) {

			final Service service = this.client.services().withName(serviceId).get();

			final Map<String, String> serviceMetadata = new HashMap<>();
			KubernetesDiscoveryProperties.Metadata metadataProps = this.properties
					.getMetadata();
			if (metadataProps.isAddLabels()) {
				Map<String, String> labelMetadata = getMapWithPrefixedKeys(
						service.getMetadata().getLabels(),
						metadataProps.getLabelsPrefix());
				if (log.isDebugEnabled()) {
					log.debug("Adding label metadata: " + labelMetadata);
				}
				serviceMetadata.putAll(labelMetadata);
			}
			if (metadataProps.isAddAnnotations()) {
				Map<String, String> annotationMetadata = getMapWithPrefixedKeys(
						service.getMetadata().getAnnotations(),
						metadataProps.getAnnotationsPrefix());
				if (log.isDebugEnabled()) {
					log.debug("Adding annotation metadata: " + annotationMetadata);
				}
				serviceMetadata.putAll(annotationMetadata);
			}

			for (EndpointSubset s : subsets) {
				// Extend the service metadata map with per-endpoint port information (if
				// requested)
				Map<String, String> endpointMetadata = new HashMap<>(serviceMetadata);
				if (metadataProps.isAddPorts()) {
					Map<String, String> ports = s.getPorts().stream()
							.filter(port -> !StringUtils.isEmpty(port.getName()))
							.collect(toMap(EndpointPort::getName,
									port -> Integer.toString(port.getPort())));
					Map<String, String> portMetadata = getMapWithPrefixedKeys(ports,
							metadataProps.getPortsPrefix());
					if (log.isDebugEnabled()) {
						log.debug("Adding port metadata: " + portMetadata);
					}
					endpointMetadata.putAll(portMetadata);
				}

				List<EndpointAddress> addresses = s.getAddresses();
				for (EndpointAddress endpointAddress : addresses) {
					String instanceId = null;
					if (endpointAddress.getTargetRef() != null) {
						instanceId = endpointAddress.getTargetRef().getUid();
					}

					EndpointPort endpointPort = findEndpointPort(s);
					instances.add(new KubernetesServiceInstance(instanceId, serviceId,
							endpointAddress, endpointPort, endpointMetadata,
							this.isServicePortSecureResolver
									.resolve(new DefaultIsServicePortSecureResolver.Input(
											endpointPort.getPort(),
											service.getMetadata().getName(),
											service.getMetadata().getLabels(),
											service.getMetadata().getAnnotations()))));
				}
			}
		}

		return instances;
	}

	private EndpointPort findEndpointPort(EndpointSubset s) {
		List<EndpointPort> ports = s.getPorts();
		EndpointPort endpointPort;
		if (ports.size() == 1) {
			endpointPort = ports.get(0);
		}
		else {
			Predicate<EndpointPort> portPredicate;
			if (!StringUtils.isEmpty(properties.getPrimaryPortName())) {
				portPredicate = port -> properties.getPrimaryPortName()
						.equalsIgnoreCase(port.getName());
			}
			else {
				portPredicate = port -> true;
			}
			endpointPort = ports.stream().filter(portPredicate).findAny()
					.orElseThrow(IllegalStateException::new);
		}
		return endpointPort;
	}

	private List<EndpointSubset> getSubsetsFromEndpoints(Endpoints endpoints) {
		if (endpoints == null) {
			return new ArrayList<>();
		}
		if (endpoints.getSubsets() == null) {
			return new ArrayList<>();
		}

		return endpoints.getSubsets();
	}

	// returns a new map that contain all the entries of the original map
	// but with the keys prefixed
	// if the prefix is null or empty, the map itself is returned (unchanged of course)
	private Map<String, String> getMapWithPrefixedKeys(Map<String, String> map,
			String prefix) {
		if (map == null) {
			return new HashMap<>();
		}

		// when the prefix is empty just return an map with the same entries
		if (!StringUtils.hasText(prefix)) {
			return map;
		}

		final Map<String, String> result = new HashMap<>();
		map.forEach((k, v) -> result.put(prefix + k, v));

		return result;
	}

	@Override
	public List<String> getServices() {
		String spelExpression = this.properties.getFilter();
		Predicate<Service> filteredServices;
		if (spelExpression == null || spelExpression.isEmpty()) {
			filteredServices = (Service instance) -> true;
		}
		else {
			Expression filterExpr = this.parser.parseExpression(spelExpression);
			filteredServices = (Service instance) -> {
				Boolean include = filterExpr.getValue(this.evalCtxt, instance,
						Boolean.class);
				if (include == null) {
					return false;
				}
				return include;
			};
		}
		return getServices(filteredServices);
	}

	public List<String> getServices(Predicate<Service> filter) {
		return this.kubernetesClientServicesFunction.apply(this.client).list().getItems()
				.stream().filter(filter).map(s -> s.getMetadata().getName())
				.collect(Collectors.toList());
	}

}
