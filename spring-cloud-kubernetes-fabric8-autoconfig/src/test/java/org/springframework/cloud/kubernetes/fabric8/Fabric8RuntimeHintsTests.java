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
import org.junit.jupiter.api.Test;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapConfigProperties;
import org.springframework.cloud.kubernetes.commons.config.SecretsConfigProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Fabric8RuntimeHints}.
 *
 * @author Abu Hena Mostafa Kamal
 * @since 5.0.3
 */
class Fabric8RuntimeHintsTests {

	private final Fabric8RuntimeHints hintsRegistrar = new Fabric8RuntimeHints();

	@Test
	void fabric8ClientClassesShouldBeRegistered() {
		RuntimeHints hints = new RuntimeHints();
		hintsRegistrar.registerHints(hints, null);

		assertThat(RuntimeHintsPredicates.reflection().onType(KubernetesClientImpl.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(KubernetesClientBuilder.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(Config.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(ConfigBuilder.class)).accepts(hints);
	}

	@Test
	void kubernetesResourcesShouldBeRegistered() {
		RuntimeHints hints = new RuntimeHints();
		hintsRegistrar.registerHints(hints, null);

		assertThat(RuntimeHintsPredicates.reflection().onType(ConfigMap.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(ConfigMapList.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(ObjectMeta.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(Secret.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(SecretList.class)).accepts(hints);
	}

	@Test
	void configPropertiesShouldBeRegistered() {
		RuntimeHints hints = new RuntimeHints();
		hintsRegistrar.registerHints(hints, null);

		assertThat(RuntimeHintsPredicates.reflection().onType(ConfigMapConfigProperties.class)).accepts(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(SecretsConfigProperties.class)).accepts(hints);
	}

	@Test
	void unrelatedClassesShouldNotBeRegistered() {
		RuntimeHints hints = new RuntimeHints();
		hintsRegistrar.registerHints(hints, null);

		assertThat(RuntimeHintsPredicates.reflection().onType(String.class)).rejects(hints);
		assertThat(RuntimeHintsPredicates.reflection().onType(Integer.class)).rejects(hints);
	}

}
