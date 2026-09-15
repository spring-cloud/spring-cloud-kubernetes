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

package org.springframework.cloud.kubernetes.fabric8.config.reload;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.log.LogAccessor;

/**
 * Handles events emitted by a Fabric8 resource informer.
 *
 * @param <T> the Kubernetes resource type
 * @author wind57
 */
final class Fabric8ResourceEventHandler<T extends HasMetadata> implements ResourceEventHandler<T> {

	private static final LogAccessor LOG = new LogAccessor(LogFactory.getLog(Fabric8ResourceEventHandler.class));

	private final SharedIndexInformer<T> informer;

	private final Consumer<T> onEvent;

	Fabric8ResourceEventHandler(SharedIndexInformer<T> informer, Consumer<T> onEvent) {
		this.informer = informer;
		this.onEvent = onEvent;
	}

	@Override
	public void onAdd(T resource) {
		LOG.debug(resource.getKind() + " " + resource.getMetadata().getName() + " was added in namespace "
				+ resource.getMetadata().getNamespace());
		onEvent.accept(resource);
	}

	@Override
	public void onUpdate(T oldResource, T newResource) {
		LOG.debug(newResource.getKind() + " " + newResource.getMetadata().getName() + " was updated in namespace "
				+ newResource.getMetadata().getNamespace());
		if (dataEquals(oldResource, newResource)) {
			LOG.debug(() -> "data in " + newResource.getKind() + " has not changed, will not reload");
			return;
		}

		onEvent.accept(newResource);
	}

	@Override
	public void onDelete(T resource, boolean deletedFinalStateUnknown) {
		LOG.debug(resource.getKind() + " " + resource.getMetadata().getName() + " was deleted in namespace "
				+ resource.getMetadata().getNamespace());
		onEvent.accept(resource);
	}

	@Override
	public void onNothing() {
		List<T> store = informer.getStore().list();
		LOG.info("onNothing called with a store of size : " + store.size());
		LOG.info("this might be an indication of a HTTP_GONE code");
	}

	private boolean dataEquals(T oldResource, T newResource) {
		if (oldResource instanceof ConfigMap oldConfigMap && newResource instanceof ConfigMap newConfigMap) {
			Map<String, String> oldData = oldConfigMap.getData();
			Map<String, String> newData = newConfigMap.getData();
			return Objects.equals(oldData, newData);
		}

		if (oldResource instanceof Secret oldSecret && newResource instanceof Secret newSecret) {
			Map<String, String> oldData = oldSecret.getData();
			Map<String, String> newData = newSecret.getData();
			return Objects.equals(oldData, newData);
		}

		return false;
	}

}
