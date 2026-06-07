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

package org.springframework.cloud.kubernetes.fabric8;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * {@link RuntimeHintsRegistrar} for Spring Cloud Kubernetes Fabric8 native image support.
 *
 * Registers reflection, resource, and proxy hints required for GraalVM native image
 * compilation when using the Fabric8 Kubernetes client with Spring Cloud Kubernetes.
 *
 * @author Abu Hena Mostafa Kamal
 */
public class Fabric8RuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		// Fabric8 client impl - loaded reflectively via KubernetesClientBuilder
		hints.reflection()
			.registerType(TypeReference.of(KubernetesClientImpl.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS);

		// KubernetesClientBuilder - used in Fabric8AutoConfiguration
		hints.reflection()
			.registerType(TypeReference.of(KubernetesClientBuilder.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.INVOKE_DECLARED_METHODS);

		// Fabric8 Config - used in Fabric8AutoConfiguration
		hints.reflection()
			.registerType(TypeReference.of(Config.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS);

		// EnvironmentPostProcessor loaded via spring.factories
		hints.reflection()
			.registerType(TypeReference.of(Fabric8ProfileEnvironmentPostProcessor.class),
					MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

		// Kubernetes service account and kubeconfig resources
		hints.resources().registerPattern(".kube/config");
		hints.resources().registerPattern("var/run/secrets/kubernetes.io/serviceaccount/token");
		hints.resources().registerPattern("var/run/secrets/kubernetes.io/serviceaccount/ca.crt");
		hints.resources().registerPattern("var/run/secrets/kubernetes.io/serviceaccount/namespace");

	}

}
