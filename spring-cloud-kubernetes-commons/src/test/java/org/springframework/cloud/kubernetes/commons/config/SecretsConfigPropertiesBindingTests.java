/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * @author wind57
 *
 * tests that prove that binding works. We need these because we moved to a record for
 * configuration properties.
 */
class SecretsConfigPropertiesBindingTests {

	@Test
	void testWithDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
			SecretsConfigProperties props = context.getBean(SecretsConfigProperties.class);
			Assertions.assertNotNull(props);
			Assertions.assertFalse(props.enableApi());
			Assertions.assertTrue(props.paths().isEmpty());
			Assertions.assertTrue(props.sources().isEmpty());
			Assertions.assertTrue(props.labels().isEmpty());
			Assertions.assertTrue(props.enabled());
			Assertions.assertNull(props.name());
			Assertions.assertNull(props.namespace());
			Assertions.assertFalse(props.useNameAsPrefix());
			Assertions.assertTrue(props.includeProfileSpecificSources());
			Assertions.assertFalse(props.failFast());

			Assertions.assertNotNull(props.retry());
			Assertions.assertEquals(props.retry().initialInterval(), 1000L);
			Assertions.assertEquals(props.retry().multiplier(), 1.1D);
			Assertions.assertEquals(props.retry().maxInterval(), 2000L);
			Assertions.assertEquals(props.retry().maxAttempts(), 6);
			Assertions.assertTrue(props.retry().enabled());
		});
	}

	@Test
	void testWithNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
				.withPropertyValues("spring.cloud.kubernetes.secrets.enableApi=false",
						"spring.cloud.kubernetes.secrets.paths[0]=a", "spring.cloud.kubernetes.secrets.paths[1]=b",
						"spring.cloud.kubernetes.secrets.sources[0].name=source-a",
						"spring.cloud.kubernetes.secrets.sources[0].namespace=source-namespace-a",
						"spring.cloud.kubernetes.secrets.sources[0].labels.key=source-value",
						"spring.cloud.kubernetes.secrets.sources[0].explicit-prefix=source-prefix",
						"spring.cloud.kubernetes.secrets.sources[0].use-name-as-prefix=true",
						"spring.cloud.kubernetes.secrets.sources[0].include-profile-specific-sources=true",
						"spring.cloud.kubernetes.secrets.labels.label-a=label-a",
						"spring.cloud.kubernetes.secrets.enabled=false", "spring.cloud.kubernetes.secrets.name=name",
						"spring.cloud.kubernetes.secrets.namespace=namespace",
						"spring.cloud.kubernetes.secrets.use-name-as-prefix=true",
						"spring.cloud.kubernetes.secrets.include-profile-specific-sources=true",
						"spring.cloud.kubernetes.secrets.fail-fast=true",
						"spring.cloud.kubernetes.secrets.retry.initial-interval=1",
						"spring.cloud.kubernetes.secrets.retry.multiplier=1.2",
						"spring.cloud.kubernetes.secrets.retry.max-interval=3",
						"spring.cloud.kubernetes.secrets.retry.max-attempts=4",
						"spring.cloud.kubernetes.secrets.retry.enabled=false")
				.run(context -> {
					SecretsConfigProperties props = context.getBean(SecretsConfigProperties.class);
					Assertions.assertNotNull(props);
					Assertions.assertFalse(props.enableApi());

					Assertions.assertEquals(props.paths().size(), 2);
					Assertions.assertEquals(props.paths().get(0), "a");
					Assertions.assertEquals(props.paths().get(1), "b");

					Assertions.assertEquals(props.sources().size(), 1);
					SecretsConfigProperties.Source source = props.sources().get(0);
					Assertions.assertEquals(source.name(), "source-a");
					Assertions.assertEquals(source.namespace(), "source-namespace-a");
					Assertions.assertEquals(source.labels().size(), 1);
					Assertions.assertEquals(source.labels().get("key"), "source-value");
					Assertions.assertEquals(source.explicitPrefix(), "source-prefix");
					Assertions.assertTrue(source.useNameAsPrefix());
					Assertions.assertTrue(source.includeProfileSpecificSources());

					Assertions.assertEquals(props.labels().size(), 1);
					Assertions.assertEquals(props.labels().get("label-a"), "label-a");

					Assertions.assertFalse(props.enabled());
					Assertions.assertEquals(props.name(), "name");
					Assertions.assertEquals(props.namespace(), "namespace");
					Assertions.assertTrue(props.useNameAsPrefix());
					Assertions.assertTrue(props.includeProfileSpecificSources());
					Assertions.assertTrue(props.failFast());

					RetryProperties retryProperties = props.retry();
					Assertions.assertNotNull(retryProperties);
					Assertions.assertEquals(retryProperties.initialInterval(), 1);
					Assertions.assertEquals(retryProperties.multiplier(), 1.2);
					Assertions.assertEquals(retryProperties.maxInterval(), 3);
					Assertions.assertFalse(retryProperties.enabled());

				});
	}

	@Configuration
	@EnableConfigurationProperties(SecretsConfigProperties.class)
	static class Config {

	}

}
