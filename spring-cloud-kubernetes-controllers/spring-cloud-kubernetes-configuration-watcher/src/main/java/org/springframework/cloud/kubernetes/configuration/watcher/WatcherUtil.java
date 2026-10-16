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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.cloud.client.ServiceInstance;
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

	static void onEvent(KubernetesObject kubernetesObject, long refreshDelay, Scheduler scheduler,
			Function<KubernetesSource, Mono<Void>> triggerRefresh) {

		KubernetesSource kubernetesSource = kubernetesSource(kubernetesObject);

		// we need defer, because otherwise triggerRefresh.apply(kubernetesSource) is
		// called when the pipeline is being built, not when it's run.
		Mono.delay(Duration.ofMillis(refreshDelay), scheduler)
			.then(Mono.defer(() -> triggerRefresh.apply(kubernetesSource)))
			.doOnSuccess(ignored -> LOG.debug(() -> "Finished refreshing " + kubernetesSource.description()))
			.doOnError(t -> LOG.warn(t, "Error when refreshing " + kubernetesSource.description()))
			.subscribe();
	}

	static boolean isSpringCloudKubernetes(KubernetesObject kubernetesObject, String label) {
		if (kubernetesObject.getMetadata() == null) {
			return false;
		}
		return Boolean.parseBoolean(labels(kubernetesObject).getOrDefault(label, "false"));
	}

	static boolean matchesByLabels(ServiceInstance serviceInstance, Map<String, String> inputLabels) {
		Map<String, String> metadata = serviceInstance.getMetadata();

		LOG.debug(() -> "Matching input labels : " + inputLabels + " against service instance "
				+ serviceInstance.getServiceId() + "/" + serviceInstance.getInstanceId() + " on metadata " + metadata);

		return inputLabels.entrySet().stream().allMatch(entry -> entry.getValue().equals(metadata.get(entry.getKey())));
	}

	private static Map<String, String> labels(KubernetesObject kubernetesObject) {
		return Optional.ofNullable(kubernetesObject.getMetadata()).map(V1ObjectMeta::getLabels).orElse(Map.of());
	}

}
