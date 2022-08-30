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

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.kubernetes.client.common.KubernetesObject;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.log.LogAccessor;

/**
 * A common place where 'onEvent' code delegates to.
 *
 * @author wind57
 */
final class WatcherUtil {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(WatcherUtil.class));

	private WatcherUtil() {
	}

	static void onEvent(KubernetesObject kubernetesObject, String label,
			long refreshDelay, ScheduledExecutorService executorService, String type,
			Function<KubernetesObject, Mono<Void>> triggerRefresh) {

		String name = kubernetesObject.getMetadata().getName();
		boolean isSpringCloudKubernetes = isSpringCloudKubernetes(kubernetesObject, label);

		if (isSpringCloudKubernetes) {

			LOG.debug(() -> "Scheduling remote refresh event to be published for " + type + ": " + name
					+ " to be published in " + refreshDelay + " milliseconds");
			executorService.schedule(() -> {
				try {
					triggerRefresh.apply(kubernetesObject).subscribe();
				}
				catch (Throwable t) {
					LOG.warn(t, "Error when refreshing ConfigMap " + name);
				}
			}, refreshDelay, TimeUnit.MILLISECONDS);
		}
		else {
			LOG.debug(() -> "Not publishing event." + type + ": + name + does not contain the label " + label);
		}
	}

	private static boolean isSpringCloudKubernetes(KubernetesObject kubernetesObject, String label) {
		if (kubernetesObject.getMetadata() == null) {
			return false;
		}
		return Boolean.parseBoolean(Optional.ofNullable(kubernetesObject.getMetadata().getLabels())
				.orElse(Collections.emptyMap()).getOrDefault(label, "false"));
	}

}
