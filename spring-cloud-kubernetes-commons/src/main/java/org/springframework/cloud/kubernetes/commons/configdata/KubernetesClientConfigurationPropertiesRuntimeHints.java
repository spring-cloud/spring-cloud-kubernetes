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

package org.springframework.cloud.kubernetes.commons.configdata;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.context.properties.bind.BindableRuntimeHintsRegistrar;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SourceConfigProperties;

/**
 * Registers runtime hints needed to bind Spring Cloud Kubernetes configuration properties in
 * a native image.
 */
class KubernetesClientConfigurationPropertiesRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		BindableRuntimeHintsRegistrar
			.forTypes(ConfigMapConfigProperties.class, SecretsConfigProperties.class, RetryProperties.class,
					SourceConfigProperties.Source.class)
			.registerHints(hints);
		registerRecordComponentAccessors(hints, RetryProperties.class);
		registerRecordComponentAccessors(hints, SourceConfigProperties.Source.class);
	}

	private void registerRecordComponentAccessors(RuntimeHints hints, Class<?> type) {
		for (var component : type.getRecordComponents()) {
			hints.reflection().registerMethod(component.getAccessor(), ExecutableMode.INVOKE);
		}
	}

}
