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

package org.springframework.cloud.kubernetes.fabric8.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;

/**
 * A context/holder for various data needed to compute property sources.
 *
 * @author wind57
 */
final class Fabric8ConfigContext {

	private Fabric8ConfigContext() {
		throw new AssertionError("no instance provided");
	}

	private final KubernetesClient client;

	private final boolean failFast;

	private final NormalizedSource normalizedSource;

	private final String target;

	Fabric8ConfigContext(KubernetesClient client, boolean failFast,
			NormalizedSource normalizedSource, String target) {
		this.client = client;
		this.failFast = failFast;
		this.normalizedSource = normalizedSource;
		this.target = target;
	}

	KubernetesClient getClient() {
		return client;
	}

	boolean isFailFast() {
		return failFast;
	}

	NormalizedSource getNormalizedSource() {
		return normalizedSource;
	}

	String getTarget() {
		return target;
	}
}
