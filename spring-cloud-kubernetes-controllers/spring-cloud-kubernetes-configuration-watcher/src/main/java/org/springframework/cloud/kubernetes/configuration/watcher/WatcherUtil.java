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

package org.springframework.cloud.kubernetes.configuration.watcher;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.configuration.watcher.KubernetesSource.fromK8sType;

/**
 * A common place where 'onEvent' code delegates to.
 *
 * @author wind57
 */
final class WatcherUtil {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(WatcherUtil.class));

	private WatcherUtil() {
	}

	static void onEvent(KubernetesObject kubernetesObject, long refreshDelay, ScheduledExecutorService executorService,
			BiFunction<KubernetesObject, String, Mono<Void>> triggerRefresh) {

		KubernetesSource source = fromK8sType(kubernetesObject);

		String name = kubernetesObject.getMetadata().getName();
		boolean isSpringCloudKubernetes = isSpringCloudKubernetes(kubernetesObject, source.label());

		if (isSpringCloudKubernetes) {

			Set<String> apps = apps(kubernetesObject, source.annotation());

			if (apps.isEmpty()) {
				apps.add(name);
			}

			LOG.info(() -> "will schedule remote refresh based on apps : " + apps);
			apps.forEach(appName -> schedule(source.description(), appName, refreshDelay, executorService,
					triggerRefresh, kubernetesObject));

		}
		else {
			LOG.debug(() -> "Not publishing event : " + source.description() + ": " + name
					+ " does not contain the label " + source.label());
		}
	}

	/**
	 * @deprecated for removal in the next major release, in favor of informer-side
	 * filtering via {@code spring.cloud.kubernetes.reload.config-maps-labels} and
	 * {@code spring.cloud.kubernetes.reload.secrets-labels}.
	 * <p>
	 * Today the configuration watcher receives all ConfigMap/Secret events from the
	 * informer and filters them locally by legacy labels. With informer label selectors
	 * configured, only matching sources are delivered, so this extra local check is no
	 * longer needed.
	 */
	@Deprecated(forRemoval = true)
	static boolean isSpringCloudKubernetes(KubernetesObject kubernetesObject, String label) {
		if (kubernetesObject.getMetadata() == null) {
			return false;
		}
		return Boolean.parseBoolean(labels(kubernetesObject).getOrDefault(label, "false"));
	}

	static Set<String> apps(KubernetesObject kubernetesObject, String annotationName) {

		// mutable on purpose
		Set<String> apps = new HashSet<>(1);
		Map<String, String> annotations = annotations(kubernetesObject);

		if (annotations.isEmpty()) {
			LOG.debug(() -> annotationName + " not present (empty data)");
			return apps;
		}

		String appsValue = annotations.get(annotationName);

		if (appsValue == null) {
			LOG.debug(() -> annotationName + " not present (missing in annotations)");
			return apps;
		}

		if (appsValue.isBlank()) {
			LOG.debug(() -> appsValue + " not present (blanks only)");
			return apps;
		}

		return Arrays.stream(appsValue.split(",")).map(String::trim).collect(Collectors.toSet());
	}

	static Map<String, String> labels(KubernetesObject kubernetesObject) {
		return Optional.ofNullable(kubernetesObject.getMetadata()).map(V1ObjectMeta::getLabels).orElse(Map.of());
	}

	static Map<String, String> annotations(KubernetesObject kubernetesObject) {
		return Optional.ofNullable(kubernetesObject.getMetadata()).map(V1ObjectMeta::getAnnotations).orElse(Map.of());
	}

	private static void schedule(String type, String appName, long refreshDelay,
			ScheduledExecutorService executorService, BiFunction<KubernetesObject, String, Mono<Void>> triggerRefresh,
			KubernetesObject kubernetesObject) {
		LOG.debug(() -> "Scheduling remote refresh event to be published for " + type + ": with appName : " + appName
				+ " to be published in " + refreshDelay + " milliseconds");
		executorService.schedule(() -> {
			try {
				triggerRefresh.apply(kubernetesObject, appName).subscribe();
			}
			catch (Throwable t) {
				LOG.warn(t, "Error when refreshing appName " + appName);
			}
		}, refreshDelay, TimeUnit.MILLISECONDS);
	}

}
