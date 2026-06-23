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

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import io.kubernetes.client.common.KubernetesObject;
import reactor.core.publisher.Mono;

import org.springframework.core.log.LogAccessor;

import static org.springframework.cloud.kubernetes.configuration.watcher.KubernetesSourceProvider.kubernetesSource;

/**
 * A common place where 'onEvent' code delegates to.
 *
 * @author wind57
 */
final class WatcherUtil {

	private static final LogAccessor LOG = new LogAccessor(WatcherUtil.class);

	private WatcherUtil() {
	}

	static void onEvent(KubernetesObject kubernetesObject, long refreshDelay, ScheduledExecutorService executorService,
			BiFunction<KubernetesObject, String, Mono<Void>> triggerRefresh) {

		KubernetesSource source = kubernetesSource(kubernetesObject);

		if (!source.serviceLabels().isEmpty()) {
			LOG.info(() -> "Using service labels for discovery : " + source.serviceLabels());
			return;
		}
		if (!source.serviceNames().isEmpty()) {
			LOG.info(() -> "Using service names for discovery : " + source.serviceNames());
			Set<String> serviceNames = source.serviceNames();

			LOG.info(() -> "will schedule remote refresh based on apps : " + serviceNames);
			serviceNames.forEach(serviceName -> schedule(source.description(), serviceName, refreshDelay,
					executorService, triggerRefresh, kubernetesObject));
		}
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
