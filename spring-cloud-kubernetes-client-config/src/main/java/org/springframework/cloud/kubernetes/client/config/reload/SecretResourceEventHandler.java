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

import java.util.function.Consumer;

import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import jakarta.annotation.Nullable;

import org.springframework.core.log.LogAccessor;

final class SecretResourceEventHandler implements ResourceEventHandler<V1Secret> {

	private static final LogAccessor LOG = new LogAccessor(SecretResourceEventHandler.class);

	private final Consumer<V1Secret> onEvent;

	@Nullable
	private final Consumer<NamespaceAndResourceVersion> resourceVersionWriter;

	SecretResourceEventHandler(Consumer<V1Secret> onEvent,
			@Nullable Consumer<NamespaceAndResourceVersion> resourceVersionWriter) {
		this.onEvent = onEvent;
		this.resourceVersionWriter = resourceVersionWriter;
	}

	@Override
	public void onAdd(V1Secret secret) {
		LOG.debug(() -> "Secret " + secret.getMetadata().getName() + " was added in namespace "
				+ secret.getMetadata().getNamespace());
		onEvent.accept(secret);
		writeResourceVersion(secret);
	}

	@Override
	public void onUpdate(V1Secret oldSecret, V1Secret newSecret) {
		LOG.debug(() -> "Secret " + newSecret.getMetadata().getName() + " was updated in namespace "
				+ newSecret.getMetadata().getNamespace());

		if (KubernetesClientEventBasedSecretsChangeDetector.equals(oldSecret.getData(), newSecret.getData())) {
			LOG.debug(() -> "data in secret has not changed, will not reload");
		}
		else {
			onEvent.accept(newSecret);
		}
		writeResourceVersion(newSecret);
	}

	@Override
	public void onDelete(V1Secret secret, boolean deletedFinalStateUnknown) {
		LOG.debug(() -> "Secret " + secret.getMetadata().getName() + " was deleted in namespace "
				+ secret.getMetadata().getNamespace());
		onEvent.accept(secret);
		writeResourceVersion(secret);
	}

	private void writeResourceVersion(V1Secret secret) {
		if (resourceVersionWriter != null) {
			V1ObjectMeta metadata = secret.getMetadata();
			resourceVersionWriter
				.accept(new NamespaceAndResourceVersion(metadata.getNamespace(), metadata.getResourceVersion()));
		}
	}

}
