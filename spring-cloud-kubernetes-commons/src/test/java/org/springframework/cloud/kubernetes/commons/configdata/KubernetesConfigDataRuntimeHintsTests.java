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

import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.RetryProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SourceConfigProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the runtime hints used for binding Kubernetes ConfigData properties.
 */
class KubernetesConfigDataRuntimeHintsTests {

	@Test
	void registersConstructorBindingHints() {
		RuntimeHints hints = new RuntimeHints();
		new KubernetesConfigDataRuntimeHints().registerHints(hints, getClass().getClassLoader());

		assertConstructorHint(hints, ConfigMapConfigProperties.class);
		assertConstructorHint(hints, SecretsConfigProperties.class);
		assertConstructorHint(hints, RetryProperties.class);
		assertConstructorHint(hints, SourceConfigProperties.Source.class);
		assertRecordComponentAccessors(hints, RetryProperties.class);
		assertRecordComponentAccessors(hints, SourceConfigProperties.Source.class);
	}

	private void assertConstructorHint(RuntimeHints hints, Class<?> type) {
		assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(type.getDeclaredConstructors()[0]))
			.accepts(hints);
	}

	private void assertRecordComponentAccessors(RuntimeHints hints, Class<?> type) {
		for (var component : type.getRecordComponents()) {
			assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(component.getAccessor())).accepts(hints);
		}
	}

}
