/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.kubernetes.commons.PodUtils;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author Ryan Baxter
 */
public final class KubernetesDiscoveryClientHealthIndicatorInitializer implements InitializingBean {

	private final PodUtils<?> podUtils;

	private final ApplicationEventPublisher applicationEventPublisher;

	public KubernetesDiscoveryClientHealthIndicatorInitializer(PodUtils<?> podUtils,
			ApplicationEventPublisher applicationEventPublisher) {
		this.podUtils = podUtils;
		this.applicationEventPublisher = applicationEventPublisher;
	}


	@Override
	public void afterPropertiesSet() {
		this.applicationEventPublisher.publishEvent(new InstanceRegisteredEvent<>(podUtils.currentPod(), null));
	}

}
