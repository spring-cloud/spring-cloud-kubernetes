/*
 * Copyright 2013-2023 the original author or authors.
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.CoreV1EndpointPort;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.wait.Wait;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.ServiceMetadata;
import org.springframework.core.log.LogAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.CollectionUtils;

import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.UNSET_PORT_NAME;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author wind57
 */
final class KubernetesDiscoveryClientUtils {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(KubernetesDiscoveryClientUtils.class));

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private static final SimpleEvaluationContext EVALUATION_CONTEXT = SimpleEvaluationContext.forReadOnlyDataBinding()
			.withInstanceMethods().build();

	private KubernetesDiscoveryClientUtils() {

	}

	static boolean matchesServiceLabels(V1Service service, KubernetesDiscoveryProperties properties) {

		Map<String, String> propertiesServiceLabels = properties.serviceLabels();
		Map<String, String> serviceLabels = Optional.ofNullable(service.getMetadata()).map(V1ObjectMeta::getLabels)
				.orElse(Map.of());

		if (propertiesServiceLabels.isEmpty()) {
			LOG.debug(() -> "service labels from properties are empty, service with name : '"
					+ service.getMetadata().getName() + "' will match");
			return true;
		}

		if (serviceLabels.isEmpty()) {
			LOG.debug(() -> "service with name : '" + service.getMetadata().getName() + "' does not have labels");
			return false;
		}

		LOG.debug(() -> "Service labels from properties : " + propertiesServiceLabels);
		LOG.debug(() -> "Service labels from service : " + serviceLabels);

		return serviceLabels.entrySet().containsAll(propertiesServiceLabels.entrySet());

	}

	static Predicate<V1Service> filter(KubernetesDiscoveryProperties properties) {
		String spelExpression = properties.filter();
		Predicate<V1Service> predicate;
		if (spelExpression == null || spelExpression.isEmpty()) {
			LOG.debug(() -> "filter not defined, returning always true predicate");
			predicate = service -> true;
		}
		else {
			Expression filterExpr = PARSER.parseExpression(spelExpression);
			predicate = service -> {
				Boolean include = filterExpr.getValue(EVALUATION_CONTEXT, service, Boolean.class);
				return Optional.ofNullable(include).orElse(false);
			};
			LOG.debug(() -> "returning predicate based on filter expression: " + spelExpression);
		}
		return predicate;
	}

	static void postConstruct(List<SharedInformerFactory> sharedInformerFactories,
			KubernetesDiscoveryProperties properties, Supplier<Boolean> informersReadyFunc,
			List<Lister<V1Service>> serviceListers) {

		sharedInformerFactories.forEach(SharedInformerFactory::startAllRegisteredInformers);
		if (!Wait.poll(Duration.ofSeconds(1), Duration.ofSeconds(properties.cacheLoadingTimeoutSeconds()), () -> {
			LOG.info(() -> "Waiting for the cache of informers to be fully loaded..");
			return informersReadyFunc.get();
		})) {
			if (properties.waitCacheReady()) {
				throw new IllegalStateException(
						"Timeout waiting for informers cache to be ready, is the kubernetes service up?");
			}
			else {
				LOG.warn(() -> "Timeout waiting for informers cache to be ready, "
						+ "ignoring the failure because waitForInformerCacheReady property is false");
			}
		}
		else {
			LOG.info(() -> "Cache fully loaded (total " + serviceListers.stream().mapToLong(x -> x.list().size()).sum()
					+ " services), discovery client is now available");
		}

	}

	static ServiceMetadata serviceMetadata(V1Service service) {
		V1ObjectMeta metadata = service.getMetadata();
		V1ServiceSpec serviceSpec = service.getSpec();
		return new ServiceMetadata(metadata.getName(), metadata.getNamespace(), serviceSpec.getType(),
				metadata.getLabels(), metadata.getAnnotations());
	}

	/**
	 * a service is allowed to have a single port defined without a name.
	 */
	static Map<String, Integer> endpointSubsetsPortData(List<V1EndpointSubset> endpointSubsets) {
		return endpointSubsets.stream()
				.flatMap(endpointSubset -> Optional.ofNullable(endpointSubset.getPorts()).orElse(List.of()).stream())
				.collect(Collectors.toMap(
						endpointPort -> hasText(endpointPort.getName()) ? endpointPort.getName() : UNSET_PORT_NAME,
						CoreV1EndpointPort::getPort));
	}

	static List<V1EndpointAddress> addresses(V1EndpointSubset endpointSubset,
			KubernetesDiscoveryProperties properties) {
		List<V1EndpointAddress> addresses = Optional.ofNullable(endpointSubset.getAddresses()).map(ArrayList::new)
				.orElse(new ArrayList<>());

		if (properties.includeNotReadyAddresses()) {
			List<V1EndpointAddress> notReadyAddresses = endpointSubset.getNotReadyAddresses();
			if (CollectionUtils.isEmpty(notReadyAddresses)) {
				return addresses;
			}
			addresses.addAll(notReadyAddresses);
		}

		return addresses;
	}

}
