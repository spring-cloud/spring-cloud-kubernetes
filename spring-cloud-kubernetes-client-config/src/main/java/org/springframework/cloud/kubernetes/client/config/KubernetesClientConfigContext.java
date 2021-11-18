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

package org.springframework.cloud.kubernetes.client.config;

import java.util.Objects;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import org.springframework.cloud.kubernetes.commons.config.NormalizedSource;

/**
 * A context/holder for various data needed to compute property sources.
 *
 * @author wind57
 */
public final class KubernetesClientConfigContext {

	private final CoreV1Api client;

	private final boolean failFast;

	private final NormalizedSource normalizedSource;

	private final String target;

	private final String appNamespace;

	public KubernetesClientConfigContext(CoreV1Api client, boolean failFast, NormalizedSource normalizedSource, String target,
			String appNamespace) {
		this.client = Objects.requireNonNull(client);
		this.failFast = failFast;
		this.normalizedSource = Objects.requireNonNull(normalizedSource);
		this.target = Objects.requireNonNull(target);
		this.appNamespace = appNamespace;
	}

	CoreV1Api getClient() {
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

	String getAppNamespace() {
		return appNamespace;
	}

}
