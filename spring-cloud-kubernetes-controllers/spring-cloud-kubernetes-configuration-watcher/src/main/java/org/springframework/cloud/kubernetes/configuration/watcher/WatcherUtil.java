/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import io.kubernetes.client.common.KubernetesObject;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.log.LogAccessor;

import static java.util.Collections.emptySet;

/**
 * A common place where 'onEvent' code delegates to.
 *
 * @author wind57
 */
final class WatcherUtil {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(WatcherUtil.class));

	private WatcherUtil() {
	}

	static void onEvent(KubernetesObject kubernetesObject, String label, String appsLabel, long refreshDelay,
			ScheduledExecutorService executorService, String type,
			BiFunction<KubernetesObject, String, Mono<Void>> triggerRefresh) {

		String name = kubernetesObject.getMetadata().getName();
		boolean isSpringCloudKubernetes = isSpringCloudKubernetes(kubernetesObject, label);

		if (isSpringCloudKubernetes) {

			Set<String> apps = apps(kubernetesObject, appsLabel);

			if (!apps.isEmpty()) {
				LOG.info(() -> "will schedule remote refresh based on apps : " + apps);
				apps.forEach(appName -> schedule(type, appName, refreshDelay, executorService, triggerRefresh,
						kubernetesObject));
			}
			else {
				LOG.info(() -> "will schedule remote refresh based on name : " + name);
				schedule(type, name, refreshDelay, executorService, triggerRefresh, kubernetesObject);
			}

		}
		else {
			LOG.debug(() -> "Not publishing event." + type + ": " + name + " does not contain the label " + label);
		}
	}

	static boolean isSpringCloudKubernetes(KubernetesObject kubernetesObject, String label) {
		if (kubernetesObject.getMetadata() == null) {
			return false;
		}
		return Boolean.parseBoolean(labels(kubernetesObject).getOrDefault(label, "false"));
	}

	static Set<String> apps(KubernetesObject kubernetesObject, String appsLabel) {
		if (kubernetesObject.getMetadata() == null) {
			LOG.debug(() -> appsLabel + " not present (no metadata)");
			return emptySet();
		}

		Map<String, String> labels = labels(kubernetesObject);
		if (labels.isEmpty()) {
			LOG.debug(() -> appsLabel + " not present (empty labels)");
			return emptySet();
		}

		String apps = labels.get(appsLabel);
		if (apps == null) {
			LOG.debug(() -> appsLabel + " not present (label not found)");
			return emptySet();
		}

		return Arrays.stream(apps.split(",")).map(String::trim).collect(Collectors.toSet());
	}

	static Map<String, String> labels(KubernetesObject kubernetesObject) {
		return Optional.ofNullable(kubernetesObject.getMetadata().getLabels()).orElse(Collections.emptyMap());
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
