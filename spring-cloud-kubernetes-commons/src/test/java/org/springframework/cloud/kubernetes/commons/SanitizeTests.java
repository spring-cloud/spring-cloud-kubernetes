/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.Sanitizer;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.MountConfigMapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;
import org.springframework.cloud.kubernetes.commons.config.SourceData;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.EnumerablePropertySource;

import static org.springframework.boot.actuate.endpoint.SanitizableData.SANITIZED_VALUE;

/**
 * @author wind57
 */
class SanitizeTests {

	private static final boolean SHOW_UNSANITIZED = true;

	private static final List<SanitizingFunction> SANITIZING_FUNCTIONS = List
			.of(new KubernetesCommonsSanitizeAutoConfiguration().secretsPropertySourceSanitizingFunction());

	@Test
	void bootstrapPropertySourceNotSecrets() {

		BootstrapPropertySource<?> bootstrapPropertySource = new BootstrapPropertySource<>(
				new EnumerablePropertySource<>("enumerable") {
					@Override
					public String[] getPropertyNames() {
						return new String[0];
					}

					@Override
					public Object getProperty(String name) {
						return null;
					}
				});

		Sanitizer sanitizer = new Sanitizer(SANITIZING_FUNCTIONS);
		SanitizableData sanitizableData = new SanitizableData(bootstrapPropertySource, "secret", "xyz");

		Assertions.assertEquals(sanitizer.sanitize(sanitizableData, SHOW_UNSANITIZED), "xyz");
	}

	@Test
	void bootstrapPropertySourceSecrets() {

		BootstrapPropertySource<?> bootstrapPropertySource = new BootstrapPropertySource<>(
				new SecretsPropertySource(new SourceData("secret-source", Map.of())));

		Sanitizer sanitizer = new Sanitizer(SANITIZING_FUNCTIONS);
		SanitizableData sanitizableData = new SanitizableData(bootstrapPropertySource, "secret", "xyz");

		Assertions.assertEquals(sanitizer.sanitize(sanitizableData, SHOW_UNSANITIZED), SANITIZED_VALUE);
	}

	@Test
	void notSecretsPropertySource() {

		BootstrapPropertySource<?> bootstrapPropertySource = new BootstrapPropertySource<>(
				new MountConfigMapPropertySource("mount-source", Map.of()));

		Sanitizer sanitizer = new Sanitizer(SANITIZING_FUNCTIONS);
		SanitizableData sanitizableData = new SanitizableData(bootstrapPropertySource, "secret", "xyz");

		Assertions.assertEquals(sanitizer.sanitize(sanitizableData, SHOW_UNSANITIZED), "xyz");
	}

	@Test
	void secretsPropertySource() {

		BootstrapPropertySource<?> bootstrapPropertySource = new BootstrapPropertySource<>(
				new SecretsPropertySource(new SourceData("secret-source", Map.of())));

		Sanitizer sanitizer = new Sanitizer(SANITIZING_FUNCTIONS);
		SanitizableData sanitizableData = new SanitizableData(bootstrapPropertySource, "secret", "xyz");

		Assertions.assertEquals(sanitizer.sanitize(sanitizableData, SHOW_UNSANITIZED), SANITIZED_VALUE);
	}

	@Test
	void compositeOneSecretOneMount() {
		CompositePropertySource compositePropertySource = new CompositePropertySource("composite");
		compositePropertySource.addFirstPropertySource(
				new SecretsPropertySource(new SourceData("secret-source", Map.of("secret", "xyz"))));
		compositePropertySource
				.addFirstPropertySource(new MountConfigMapPropertySource("mount-source", Map.of("mount", "abc")));

		Sanitizer sanitizer = new Sanitizer(SANITIZING_FUNCTIONS);
		SanitizableData sanitizableDataSecret = new SanitizableData(compositePropertySource, "secret", "xyz");
		SanitizableData sanitizableDataMount = new SanitizableData(compositePropertySource, "mount", "abc");

		Assertions.assertEquals(sanitizer.sanitize(sanitizableDataSecret, SHOW_UNSANITIZED), SANITIZED_VALUE);
		Assertions.assertEquals(sanitizer.sanitize(sanitizableDataMount, SHOW_UNSANITIZED), "abc");
	}

}
