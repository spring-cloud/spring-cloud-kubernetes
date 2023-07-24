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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.wait.Wait;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.core.log.LogAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.keysWithPrefix;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.NAMESPACE_METADATA_KEY;
import static org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryConstants.SERVICE_TYPE;

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

	/**
	 * This adds the following metadata. <pre>
	 *     - labels (if requested)
	 *     - annotations (if requested)
	 *     - metadata
	 *     - service type
	 * </pre>
	 */
	static Map<String, String> serviceMetadata(KubernetesDiscoveryProperties properties, V1Service service,
			String serviceId) {

		Map<String, String> serviceMetadata = new HashMap<>();
		KubernetesDiscoveryProperties.Metadata metadataProps = properties.metadata();
		if (metadataProps.addLabels()) {
			Map<String, String> labelMetadata = keysWithPrefix(service.getMetadata().getLabels(),
					metadataProps.labelsPrefix());
			LOG.debug(() -> "Adding labels metadata: " + labelMetadata + " for serviceId: " + serviceId);
			serviceMetadata.putAll(labelMetadata);
		}
		if (metadataProps.addAnnotations()) {
			Map<String, String> annotationMetadata = keysWithPrefix(service.getMetadata().getAnnotations(),
					metadataProps.annotationsPrefix());
			LOG.debug(() -> "Adding annotations metadata: " + annotationMetadata + " for serviceId: " + serviceId);
			serviceMetadata.putAll(annotationMetadata);
		}

		serviceMetadata.put(NAMESPACE_METADATA_KEY,
				Optional.ofNullable(service.getMetadata()).map(V1ObjectMeta::getNamespace).orElse(null));
		serviceMetadata.put(SERVICE_TYPE,
				Optional.ofNullable(service.getSpec()).map(V1ServiceSpec::getType).orElse(null));

		return serviceMetadata;
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

}
