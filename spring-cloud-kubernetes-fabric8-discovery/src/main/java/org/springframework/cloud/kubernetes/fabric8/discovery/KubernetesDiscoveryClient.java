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
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance.NAMESPACE_METADATA_KEY;

/**
 * Kubernetes implementation of {@link DiscoveryClient}.
 *
 * @author Ioannis Canellos
 * @author Tim Ysewyn
 */
public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClient.class);

	private static final String PRIMARY_PORT_NAME_LABEL_KEY = "primary-port-name";

	private static final String HTTPS_PORT_NAME = "https";

	private static final String HTTP_PORT_NAME = "http";

	private final KubernetesDiscoveryProperties properties;

	private final ServicePortSecureResolver servicePortSecureResolver;

	private final KubernetesClientServicesFunction kubernetesClientServicesFunction;

	private final SpelExpressionParser parser = new SpelExpressionParser();

	private final SimpleEvaluationContext evalCtxt = SimpleEvaluationContext.forReadOnlyDataBinding()
			.withInstanceMethods().build();

	private KubernetesClient client;

	public KubernetesDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction) {

		this(client, kubernetesDiscoveryProperties, kubernetesClientServicesFunction,
				new ServicePortSecureResolver(kubernetesDiscoveryProperties));
	}

	KubernetesDiscoveryClient(KubernetesClient client, KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
			KubernetesClientServicesFunction kubernetesClientServicesFunction,
			ServicePortSecureResolver servicePortSecureResolver) {

		this.client = client;
		this.properties = kubernetesDiscoveryProperties;
		this.kubernetesClientServicesFunction = kubernetesClientServicesFunction;
		this.servicePortSecureResolver = servicePortSecureResolver;
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
		Assert.notNull(serviceId, "[Assertion failed] - the object argument must not be null");

		List<EndpointSubsetNS> subsetsNS = this.getEndPointsList(serviceId).stream().map(this::getSubsetsFromEndpoints)
				.collect(Collectors.toList());

		List<ServiceInstance> instances = new ArrayList<>();
		if (!subsetsNS.isEmpty()) {
			for (EndpointSubsetNS es : subsetsNS) {
				instances.addAll(this.getNamespaceServiceInstances(es, serviceId));
			}
		}

		return instances;
	}

	public List<Endpoints> getEndPointsList(String serviceId) {
		return this.properties.isAllNamespaces()
				? this.client.endpoints().inAnyNamespace().withField("metadata.name", serviceId)
						.withLabels(properties.getServiceLabels()).list().getItems()
				: this.client.endpoints().withField("metadata.name", serviceId)
						.withLabels(properties.getServiceLabels()).list().getItems();
	}

	private List<ServiceInstance> getNamespaceServiceInstances(EndpointSubsetNS es, String serviceId) {
		String namespace = es.getNamespace();
		List<EndpointSubset> subsets = es.getEndpointSubset();
		List<ServiceInstance> instances = new ArrayList<>();
		if (!subsets.isEmpty()) {
			final Service service = this.client.services().inNamespace(namespace).withName(serviceId).get();
			final Map<String, String> serviceMetadata = this.getServiceMetadata(service);
			KubernetesDiscoveryProperties.Metadata metadataProps = this.properties.getMetadata();

			String primaryPortName = this.properties.getPrimaryPortName();
			Map<String, String> labels = service.getMetadata().getLabels();
			if (labels != null && labels.containsKey(PRIMARY_PORT_NAME_LABEL_KEY)) {
				primaryPortName = labels.get(PRIMARY_PORT_NAME_LABEL_KEY);
			}

			for (EndpointSubset s : subsets) {
				// Extend the service metadata map with per-endpoint port information (if
				// requested)
				Map<String, String> endpointMetadata = new HashMap<>(serviceMetadata);
				if (metadataProps.isAddPorts()) {
					Map<String, String> ports = s.getPorts().stream()
							.filter(port -> StringUtils.hasText(port.getName()))
							.collect(toMap(EndpointPort::getName, port -> Integer.toString(port.getPort())));
					Map<String, String> portMetadata = getMapWithPrefixedKeys(ports, metadataProps.getPortsPrefix());
					if (log.isDebugEnabled()) {
						log.debug("Adding port metadata: " + portMetadata);
					}
					endpointMetadata.putAll(portMetadata);
				}

				if (this.properties.isAllNamespaces()) {
					endpointMetadata.put(NAMESPACE_METADATA_KEY, namespace);
				}

				List<EndpointAddress> addresses = s.getAddresses();

				if (this.properties.isIncludeNotReadyAddresses()
						&& !CollectionUtils.isEmpty(s.getNotReadyAddresses())) {
					if (addresses == null) {
						addresses = new ArrayList<>();
					}
					addresses.addAll(s.getNotReadyAddresses());
				}

				for (EndpointAddress endpointAddress : addresses) {
					int endpointPort = findEndpointPort(s, serviceId, primaryPortName);
					String instanceId = null;
					if (endpointAddress.getTargetRef() != null) {
						instanceId = endpointAddress.getTargetRef().getUid();
					}
					instances.add(new KubernetesServiceInstance(instanceId, serviceId, endpointAddress.getIp(),
							endpointPort, endpointMetadata,
							this.servicePortSecureResolver.resolve(new ServicePortSecureResolver.Input(endpointPort,
									service.getMetadata().getName(), service.getMetadata().getLabels(),
									service.getMetadata().getAnnotations()))));
				}
			}
		}

		return instances;
	}

	private Map<String, String> getServiceMetadata(Service service) {
		final Map<String, String> serviceMetadata = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = this.properties.getMetadata();
		if (metadataProps.isAddLabels()) {
			Map<String, String> labelMetadata = getMapWithPrefixedKeys(service.getMetadata().getLabels(),
					metadataProps.getLabelsPrefix());
			if (log.isDebugEnabled()) {
				log.debug("Adding label metadata: " + labelMetadata);
			}
			serviceMetadata.putAll(labelMetadata);
		}
		if (metadataProps.isAddAnnotations()) {
			Map<String, String> annotationMetadata = getMapWithPrefixedKeys(service.getMetadata().getAnnotations(),
					metadataProps.getAnnotationsPrefix());
			if (log.isDebugEnabled()) {
				log.debug("Adding annotation metadata: " + annotationMetadata);
			}
			serviceMetadata.putAll(annotationMetadata);
		}

		return serviceMetadata;
	}

	private int findEndpointPort(EndpointSubset s, String serviceId, String primaryPortName) {
		List<EndpointPort> endpointPorts = s.getPorts();

		if (endpointPorts.size() == 0) {
			log.debug("no ports found for service : " + serviceId + ", will return zero");
			return 0;
		}

		if (endpointPorts.size() == 1) {
			return endpointPorts.get(0).getPort();
		}
		else {
			Map<String, Integer> ports = endpointPorts.stream().filter(p -> StringUtils.hasText(p.getName()))
					.collect(Collectors.toMap(EndpointPort::getName, EndpointPort::getPort));
			// This oneliner is looking for a port with a name equal to the primary port
			// name specified in the service label
			// or in spring.cloud.kubernetes.discovery.primary-port-name, equal to https,
			// or equal to http.
			// In case no port has been found return -1 to log a warning and fall back to
			// the first port in the list.
			int discoveredPort = ports.getOrDefault(primaryPortName,
					ports.getOrDefault(HTTPS_PORT_NAME, ports.getOrDefault(HTTP_PORT_NAME, -1)));

			if (discoveredPort == -1) {
				if (StringUtils.hasText(primaryPortName)) {
					log.warn("Could not find a port named '" + primaryPortName + "', 'https', or 'http' for service '"
							+ serviceId + "'.");
				}
				else {
					log.warn("Could not find a port named 'https' or 'http' for service '" + serviceId + "'.");
				}
				log.warn(
						"Make sure that either the primary-port-name label has been added to the service, or that spring.cloud.kubernetes.discovery.primary-port-name has been configured.");
				log.warn("Alternatively name the primary port 'https' or 'http'");
				log.warn("An incorrect configuration may result in non-deterministic behaviour.");
				discoveredPort = endpointPorts.get(0).getPort();
			}
			return discoveredPort;
		}
	}

	private EndpointSubsetNS getSubsetsFromEndpoints(Endpoints endpoints) {
		EndpointSubsetNS es = new EndpointSubsetNS();
		es.setNamespace(this.client.getNamespace()); // start with the default that comes
														// with the client

		if (endpoints != null && endpoints.getSubsets() != null) {
			es.setNamespace(endpoints.getMetadata().getNamespace());
			es.setEndpointSubset(endpoints.getSubsets());
		}

		return es;
	}

	// returns a new map that contain all the entries of the original map
	// but with the keys prefixed
	// if the prefix is null or empty, the map itself is returned (unchanged of course)
	private Map<String, String> getMapWithPrefixedKeys(Map<String, String> map, String prefix) {
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
				Boolean include = filterExpr.getValue(this.evalCtxt, instance, Boolean.class);
				if (include == null) {
					return false;
				}
				return include;
			};
		}
		return getServices(filteredServices);
	}

	public List<String> getServices(Predicate<Service> filter) {
		return this.kubernetesClientServicesFunction.apply(this.client).list().getItems().stream().filter(filter)
				.map(s -> s.getMetadata().getName()).distinct().collect(Collectors.toList());
	}

	@Override
	public int getOrder() {
		return this.properties.getOrder();
	}

}
