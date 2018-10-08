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

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.Assert;

public class KubernetesDiscoveryClient implements DiscoveryClient {

	private static final Log log = LogFactory.getLog(KubernetesDiscoveryClient.class);

	private KubernetesClient client;
	private final KubernetesDiscoveryProperties properties;
	private final SpelExpressionParser parser = new SpelExpressionParser();
	private final SimpleEvaluationContext evalCtxt = SimpleEvaluationContext
														.forReadOnlyDataBinding()
														.withInstanceMethods()
														.build();

	public KubernetesDiscoveryClient(KubernetesClient client,
			KubernetesDiscoveryProperties kubernetesDiscoveryProperties) {

		this.client = client;
		this.properties = kubernetesDiscoveryProperties;
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
		final Map<String, String> labels = getLabels(serviceId);

		Endpoints endpoints = client.endpoints().withName(serviceId).get();
		List<EndpointSubset> subsets = null != endpoints ? endpoints.getSubsets() : new ArrayList<>();
		List<ServiceInstance> instances = new ArrayList<>();
		if (!subsets.isEmpty()) {
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

	private Map<String, String> getLabels(String serviceName) {
		final Service service = client.services().withName(serviceName).get();
		if (service != null) {
			return service.getMetadata().getLabels();
		}
		return Collections.emptyMap();
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
