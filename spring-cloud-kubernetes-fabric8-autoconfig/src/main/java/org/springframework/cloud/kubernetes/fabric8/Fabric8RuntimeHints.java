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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.impl.KubernetesClientImpl;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

/**
 * {@link RuntimeHintsRegistrar} for Spring Cloud Kubernetes Fabric8 native image support.
 *
 * <p>
 * Registers reflection hints required for GraalVM native image compilation when using the
 * Fabric8 Kubernetes client with Spring Cloud Kubernetes.
 *
 * @author Abu Hena Mostafa Kamal
 * @since 5.0.3
 */
public class Fabric8RuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		// ===== FABRIC8 CLIENT CLASSES =====
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

		// ConfigBuilder - used in Fabric8AutoConfiguration.kubernetesClientConfig()
		hints.reflection()
			.registerType(TypeReference.of(ConfigBuilder.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.INVOKE_DECLARED_METHODS);

		// ===== KUBERNETES RESOURCES (CRITICAL FOR CONFIGMAP READING) =====
		// ConfigMap - the actual resource being read
		hints.reflection()
			.registerType(TypeReference.of(ConfigMap.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS);

		// ConfigMapList - for listing ConfigMaps
		hints.reflection()
			.registerType(TypeReference.of(ConfigMapList.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS);

		// ObjectMeta - metadata for all Kubernetes resources
		hints.reflection()
			.registerType(TypeReference.of(ObjectMeta.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS);

		// Secret - if using secrets
		hints.reflection()
			.registerType(TypeReference.of(Secret.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS);

		hints.reflection()
			.registerType(TypeReference.of(SecretList.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
					MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS);

		hints.reflection()
			.registerType(TypeReference.of(ConfigMapConfigProperties.class),
					MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

		hints.reflection()
			.registerType(TypeReference.of(SecretsConfigProperties.class), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

		// ===== RESOURCE HINTS =====
		// Kubernetes service account and Fabric8 service files
		hints.resources().registerPattern("classpath*:META-INF/services/io.fabric8.kubernetes.client.*");

		hints.resources().registerPattern("classpath*:META-INF/fabric8/*");
	}

}
