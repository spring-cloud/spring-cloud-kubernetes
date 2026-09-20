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

package org.springframework.cloud.kubernetes.client.config.reload;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Secret;

import org.springframework.core.log.LogAccessor;

/**
 * @param <T> either a configmap or a secret
 * @author wind57
 */
final class KubernetesResourceEventHandler<T extends KubernetesObject> implements ResourceEventHandler<T> {

	private static final LogAccessor LOG = new LogAccessor(KubernetesResourceEventHandler.class);

	private final Consumer<T> onEvent;

	KubernetesResourceEventHandler(Consumer<T> onEvent) {
		this.onEvent = onEvent;
	}

	@Override
	public void onAdd(T resource) {
		LOG.debug(() -> resource.getKind() + " " + resource.getMetadata().getName() + " was added in namespace "
				+ resource.getMetadata().getNamespace());
		onEvent.accept(resource);
	}

	@Override
	public void onUpdate(T oldResource, T newResource) {
		LOG.debug(() -> newResource.getKind() + " " + newResource.getMetadata().getName() + " was updated in namespace "
				+ newResource.getMetadata().getNamespace());

		if (oldResource instanceof V1ConfigMap oldConfigMap && newResource instanceof V1ConfigMap newConfigMap) {
			Map<String, String> oldData = oldConfigMap.getData();
			Map<String, String> newData = newConfigMap.getData();
			boolean configMapDataEquals = configMapDataEquals(oldData, newData);
			if (configMapDataEquals) {
				LOG.debug(() -> "data in ConfigMap has not changed, will not reload");
				return;
			}
		}

		if (oldResource instanceof V1Secret oldSecret && newResource instanceof V1Secret newSecret) {
			Map<String, byte[]> oldData = oldSecret.getData();
			Map<String, byte[]> newData = newSecret.getData();
			boolean secretDataEquals = secretDataEquals(oldData, newData);
			if (secretDataEquals) {
				LOG.debug(() -> "data in Secret has not changed, will not reload");
				return;
			}
		}

		onEvent.accept(newResource);

	}

	@Override
	public void onDelete(T resource, boolean deletedFinalStateUnknown) {
		LOG.debug(() -> resource.getKind() + " " + resource.getMetadata().getName() + " was deleted in namespace "
				+ resource.getMetadata().getNamespace());
		onEvent.accept(resource);
	}

	boolean configMapDataEquals(Map<String, String> oldData, Map<String, String> newData) {
		return Objects.equals(oldData, newData);
	}

	boolean secretDataEquals(Map<String, byte[]> left, Map<String, byte[]> right) {
		Map<String, byte[]> innerLeft = Optional.ofNullable(left).orElse(Map.of());
		Map<String, byte[]> innerRight = Optional.ofNullable(right).orElse(Map.of());

		if (innerLeft.size() != innerRight.size()) {
			return false;
		}

		for (Map.Entry<String, byte[]> entry : innerLeft.entrySet()) {
			String key = entry.getKey();
			byte[] value = entry.getValue();
			if (!Arrays.equals(value, innerRight.get(key))) {
				return false;
			}
		}
		return true;
	}

}
