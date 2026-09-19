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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InformerResourceVersionResolverTests {

	@Test
	void returnsInformerResourceVersionWhenHaIsDisabled() {
		InformerResourceVersionResolver resolver = new InformerResourceVersionResolver(Map.of("default", "10"), false);

		assertThat(resolver.resolve("default", "20")).isEqualTo("20");
	}

	@Test
	void consumesStoredResourceVersionOnlyOncePerNamespace() {
		InformerResourceVersionResolver resolver = new InformerResourceVersionResolver(Map.of("default", "10"), true);

		assertThat(resolver.resolve("default", null)).isEqualTo("10");
		assertThat(resolver.resolve("default", "20")).isEqualTo("20");
	}

	@Test
	void consumesStoredResourceVersionIndependentlyForEachNamespace() {
		InformerResourceVersionResolver resolver = new InformerResourceVersionResolver(
				Map.of("namespace-one", "10", "namespace-two", "20"), true);

		assertThat(resolver.resolve("namespace-one", null)).isEqualTo("10");
		assertThat(resolver.resolve("namespace-two", null)).isEqualTo("20");
		assertThat(resolver.resolve("namespace-one", "11")).isEqualTo("11");
		assertThat(resolver.resolve("namespace-two", "21")).isEqualTo("21");
	}

}
