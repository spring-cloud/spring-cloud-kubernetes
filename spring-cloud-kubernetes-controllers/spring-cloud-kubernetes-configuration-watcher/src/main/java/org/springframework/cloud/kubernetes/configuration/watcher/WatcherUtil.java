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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
			Function<KubernetesSource, Mono<Void>> triggerRefresh) {

		KubernetesSource kubernetesSource = kubernetesSource(kubernetesObject);

		LOG.debug(() -> "Scheduling remote refresh event to be published for " + kubernetesSource.description() + " in "
			+ refreshDelay + " milliseconds");
		executorService.schedule(() -> {
			try {
				triggerRefresh.apply(kubernetesSource).subscribe();
			}
			catch (Throwable t) {
				LOG.warn(t, "Error when refreshing " + kubernetesSource.description());
			}
		}, refreshDelay, TimeUnit.MILLISECONDS);
	}

}
