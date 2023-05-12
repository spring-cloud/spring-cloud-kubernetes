/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.Collection;

import io.kubernetes.client.openapi.apis.CoreV1Api;

import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.retry.annotation.Retryable;

/**
 * ConfigMapPropertySourceLocator for when retry is enabled.
 *
 * @author Ryan Baxter
 */
@Order(0)
class RetryableKubernetesClientConfigMapPropertySourceLocator extends KubernetesClientConfigMapPropertySourceLocator {

	RetryableKubernetesClientConfigMapPropertySourceLocator(CoreV1Api coreV1Api, ConfigMapConfigProperties properties,
			KubernetesNamespaceProvider kubernetesNamespaceProvider) {
		super(coreV1Api, properties, kubernetesNamespaceProvider);
	}

	@Override
	@Retryable(interceptor = "kubernetesConfigRetryInterceptor")
	public PropertySource<?> locate(Environment environment) {
		return super.locate(environment);
	}

	@Override
	@Retryable(interceptor = "kubernetesConfigRetryInterceptor")
	public Collection<PropertySource<?>> locateCollection(Environment environment) {
		return super.locateCollection(environment);
	}

}
