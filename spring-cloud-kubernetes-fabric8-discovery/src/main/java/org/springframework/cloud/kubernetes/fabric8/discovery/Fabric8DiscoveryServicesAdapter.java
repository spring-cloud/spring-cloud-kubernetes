/*
 * Copyright 2012-2022 the original author or authors.
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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Adapts a {@link KubernetesClientServicesFunction} to a Function that takes a
 * KubernetesClient as input and returns a List of Services(s), plus adds functionality
 * not supported by it.
 *
 * @author wind57
 */
final class Fabric8DiscoveryServicesAdapter implements Function<KubernetesClient, List<Service>> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8DiscoveryServicesAdapter.class));

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private static final SimpleEvaluationContext EVALUATION_CONTEXT = SimpleEvaluationContext.forReadOnlyDataBinding()
			.withInstanceMethods().build();

	private final KubernetesClientServicesFunction function;

	private final KubernetesDiscoveryProperties properties;

	private final Predicate<Service> filter;

	Fabric8DiscoveryServicesAdapter(KubernetesClientServicesFunction function, KubernetesDiscoveryProperties properties,
			Predicate<Service> filter) {
		this.function = function;
		this.properties = properties;
		if (filter == null) {
			this.filter = filter();
		}
		else {
			this.filter = filter;
		}
	}

	@Override
	public List<Service> apply(KubernetesClient client) {
		if (!properties.namespaces().isEmpty()) {
			LOG.debug(() -> "searching in namespaces : " + properties.namespaces() + " with filter : "
					+ properties.filter());
			List<Service> services = new ArrayList<>();
			properties.namespaces().forEach(namespace -> services.addAll(client.services().inNamespace(namespace)
					.withLabels(properties.serviceLabels()).list().getItems().stream().filter(filter).toList()));
			return services;
		}
		return function.apply(client).list().getItems().stream().filter(filter).toList();
	}

	Predicate<Service> filter() {
		String spelExpression = properties.filter();
		Predicate<Service> predicate;
		if (spelExpression == null || spelExpression.isEmpty()) {
			predicate = service -> true;
		}
		else {
			Expression filterExpr = PARSER.parseExpression(spelExpression);
			predicate = service -> {
				Boolean include = filterExpr.getValue(EVALUATION_CONTEXT, service, Boolean.class);
				return Optional.ofNullable(include).orElse(false);
			};
		}
		return predicate;
	}

}
