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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.core.log.LogAccessor;

/**
 * Resolves the resource version for each informer request.
 *
 * <p>
 * When HA is disabled, the resolver always returns the resource version supplied by the
 * informer. When HA is enabled, it returns the persisted resource version exactly once,
 * for the first request, so that the informer can replay events from the stored
 * checkpoint. Every subsequent request must use the resource version supplied by the
 * informer because that value advances as the informer processes list and watch calls.
 *
 * @author wind57
 */
final class InformerResourceVersionResolver {

	private static final LogAccessor LOG = new LogAccessor(InformerResourceVersionResolver.class);

	// resource versions that we have in our custom lease, these are per namespace
	private final Map<String, String> checkpointResourceVersions;

	private final boolean haEnabled;

	// key is the namespace, value is whether its stored resource
	// version was already consumed or not
	private final Map<String, AtomicBoolean> checkpointResourceVersionConsumed = new ConcurrentHashMap<>();

	InformerResourceVersionResolver(Map<String, String> checkpointResourceVersions, boolean haEnabled) {
		this.checkpointResourceVersions = checkpointResourceVersions;
		this.haEnabled = haEnabled;
	}

	String resolve(String namespace, String informerResourceVersion) {

		// if HA is not enabled, we do not restore from a checkpoint
		if (!haEnabled) {
			return informerResourceVersion;
		}

		String checkpointResourceVersion = checkpointResourceVersions.get(namespace);
		// there is no previous checkpoint ( maybe it's the first time app is started in
		// HA mode)
		if (checkpointResourceVersion == null) {
			return informerResourceVersion;
		}

		// Consume the checkpoint only once.
		// Subsequent versions come from the informer, so it can progress.
		boolean checkpointNotConsumed = checkpointResourceVersionConsumed
			.computeIfAbsent(namespace, ignored -> new AtomicBoolean())
			.compareAndSet(false, true);

		if (checkpointNotConsumed) {
			LOG.info(() -> "replaying events in namespace " + namespace + " from resource version "
					+ checkpointResourceVersion);
			return checkpointResourceVersion;
		}
		return informerResourceVersion;
	}

}
