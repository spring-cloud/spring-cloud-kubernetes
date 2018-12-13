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
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;

public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClient.class);

	private KubernetesClient client;
	private final KubernetesDiscoveryProperties properties;
	private final IsServicePortSecureResolver isServicePortSecureResolver;

	private final SpelExpressionParser parser = new SpelExpressionParser();
	private final SimpleEvaluationContext evalCtxt = SimpleEvaluationContext
														.forReadOnlyDataBinding()
														.withInstanceMethods()
														.build();

	public KubernetesDiscoveryClient(KubernetesClient client,
                                     KubernetesDiscoveryProperties kubernetesDiscoveryProperties,
                                     IsServicePortSecureResolver isServicePortSecureResolver) {

		this.client = client;
		this.properties = kubernetesDiscoveryProperties;
        this.isServicePortSecureResolver = isServicePortSecureResolver;
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

	@Override
	public List<ServiceInstance> getInstances(String serviceId) {
		Assert.notNull(serviceId,
				"[Assertion failed] - the object argument must be null");

		Endpoints endpoints = client.endpoints().withName(serviceId).get();
		List<EndpointSubset> subsets = null != endpoints ? endpoints.getSubsets() : new ArrayList<>();
		List<ServiceInstance> instances = new ArrayList<>();
		if (!subsets.isEmpty()) {

			final Service service = client.services().withName(serviceId).get();

			final Map<String, String> serviceMetadata = new HashMap<>();
			KubernetesDiscoveryProperties.Metadata metadataProps = properties.getMetadata();
			if(metadataProps.isAddLabels()) {
				Map<String, String> labelMetadata = getMapWithPrefixedKeys(
					service.getMetadata().getLabels(), metadataProps.getLabelsPrefix());
				if (log.isDebugEnabled()) {
					log.debug("Adding label metadata: "+labelMetadata);
				}
				serviceMetadata.putAll(labelMetadata);
			}
			if(metadataProps.isAddAnnotations()) {
				Map<String, String> annotationMetadata = getMapWithPrefixedKeys(
					service.getMetadata().getAnnotations(), metadataProps.getAnnotationsPrefix());
				if (log.isDebugEnabled()) {
					log.debug("Adding annotation metadata: "+annotationMetadata);
				}
				serviceMetadata.putAll(annotationMetadata);
			}

			for (EndpointSubset s : subsets) {
				// Extend the service metadata map with per-endpoint port information (if requested)
				Map<String, String> endpointMetadata = new HashMap<>(serviceMetadata);
				if(metadataProps.isAddPorts()) {
					Map<String, String> ports = s.getPorts().stream()
						.filter(port -> !StringUtils.isEmpty(port.getName()))
						.collect(toMap(EndpointPort::getName, port -> Integer.toString(port.getPort())));
					Map<String, String> portMetadata = getMapWithPrefixedKeys(ports, metadataProps.getPortsPrefix());
					if (log.isDebugEnabled()) {
						log.debug("Adding port metadata: "+portMetadata);
					}
					endpointMetadata.putAll(portMetadata);
				}

				List<EndpointAddress> addresses = s.getAddresses();
				for (EndpointAddress endpointAddress : addresses) {
                    final EndpointPort endpointPort =
                            s.getPorts().stream().findFirst().orElseThrow(IllegalStateException::new);
                    instances.add(new KubernetesServiceInstance(serviceId,
							endpointAddress,
                            endpointPort,
							endpointMetadata,
                            isServicePortSecureResolver.resolve(
                                    new IsServicePortSecureResolver.Input(
                                            endpointPort.getPort(),
                                            service.getMetadata().getName(),
                                            service.getMetadata().getLabels(),
                                            service.getMetadata().getAnnotations()
                                    )
                            )));
				}
			}
		}

		return instances;
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
		String spelExpression = properties.getFilter();
		Predicate<Service> filteredServices;
		if (spelExpression == null || spelExpression.isEmpty()) {
			filteredServices = (Service instance) -> true;
		} else {
			Expression filterExpr = parser.parseExpression(spelExpression);
			filteredServices = (Service instance) -> {
				Boolean include = filterExpr.getValue(evalCtxt, instance, Boolean.class);
				if (include == null) {
					return false;
				}
				return include;
			};
		}
		return getServices(filteredServices);
	}

	public List<String> getServices(Predicate<Service> filter) {
		return client.services().list()
				.getItems()
				.stream()
				.filter(filter)
				.map(s -> s.getMetadata().getName())
				.collect(Collectors.toList());
	}

}
