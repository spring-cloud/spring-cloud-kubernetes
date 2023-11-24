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

package org.springframework.cloud.kubernetes.discovery;

import java.util.function.Supplier;

import org.springframework.cloud.kubernetes.commons.PodUtils;

/**
 * @author wind57
 */
final class KubernetesDiscoveryPodUtils implements PodUtils<Object> {

	@Override
	public Supplier<Object> currentPod() {
		// we don't really have a way to get the pod here
		return () -> null;
	}

	@Override
	public boolean isInsideKubernetes() {
		// this bean is used in a config that is annotated
		// with @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES),
		// so safe to return true here.
		return true;
	}

}
