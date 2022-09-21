/*
 * Copyright 2013-2022 the original author or authors.
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

import io.kubernetes.client.common.KubernetesObject;
import reactor.core.publisher.Mono;

/**
 * Defines the refresh trigger contract.
 *
 * @author wind57
 */
sealed interface RefreshTrigger permits BusRefreshTrigger, ConfigMapWatcherChangeDetector, HttpRefreshTrigger, SecretsWatcherChangeDetector {

	/**
	 * @param kubernetesObject either a config-map or secret at the moment.
	 * @param appName which is not necessarily equal to
	 * kubernetesObject.getMetadata().getName()
	 */
	Mono<Void> triggerRefresh(KubernetesObject kubernetesObject, String appName);

}
