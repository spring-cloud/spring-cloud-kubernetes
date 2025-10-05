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

package org.springframework.cloud.kubernetes.commons.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.springframework.cloud.kubernetes.commons.config.ReadType.BATCH;
import static org.springframework.cloud.kubernetes.commons.config.ReadType.SINGLE;

/**
 * @author wind57
 *
 * tests that prove that binding works. We need these because we moved to a record for
 * configuration properties.
 */
class ConfigMapConfigPropertiesBindingTests {

	@Test
	void testWithDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class).run(context -> {
			ConfigMapConfigProperties props = context.getBean(ConfigMapConfigProperties.class);
			Assertions.assertThat(props).isNotNull();
			Assertions.assertThat(props.sources().isEmpty()).isTrue();
			Assertions.assertThat(props.labels().isEmpty()).isTrue();
			Assertions.assertThat(props.enabled()).isTrue();
			Assertions.assertThat(props.name()).isNull();
			Assertions.assertThat(props.namespace()).isNull();
			Assertions.assertThat(props.useNameAsPrefix()).isFalse();
			Assertions.assertThat(props.includeProfileSpecificSources()).isTrue();
			Assertions.assertThat(props.failFast()).isFalse();
			Assertions.assertThat(props.readType()).isSameAs(BATCH);

			Assertions.assertThat(props.retry()).isNotNull();
			Assertions.assertThat(props.retry().initialInterval()).isEqualTo(1000L);
			Assertions.assertThat(props.retry().multiplier()).isEqualTo(1.1D);
			Assertions.assertThat(props.retry().maxInterval()).isEqualTo(2000L);
			Assertions.assertThat(props.retry().maxAttempts()).isEqualTo(6);
			Assertions.assertThat(props.retry().enabled()).isTrue();
		});
	}

	@Test
	void testWithNonDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
			.withPropertyValues("spring.cloud.kubernetes.config.sources[0].name=source-a",
					"spring.cloud.kubernetes.config.sources[0].namespace=source-namespace-a",
					"spring.cloud.kubernetes.config.sources[0].labels.key=source-value",
					"spring.cloud.kubernetes.config.sources[0].explicit-prefix=source-prefix",
					"spring.cloud.kubernetes.config.sources[0].use-name-as-prefix=true",
					"spring.cloud.kubernetes.config.sources[0].include-profile-specific-sources=true",
					"spring.cloud.kubernetes.config.labels.label-a=label-a",
					"spring.cloud.kubernetes.config.enabled=false", "spring.cloud.kubernetes.config.name=name",
					"spring.cloud.kubernetes.config.namespace=namespace",
					"spring.cloud.kubernetes.config.use-name-as-prefix=true",
					"spring.cloud.kubernetes.config.include-profile-specific-sources=true",
					"spring.cloud.kubernetes.config.fail-fast=true",
					"spring.cloud.kubernetes.config.retry.initial-interval=1",
					"spring.cloud.kubernetes.config.retry.multiplier=1.2",
					"spring.cloud.kubernetes.config.retry.max-interval=3",
					"spring.cloud.kubernetes.config.retry.max-attempts=4",
					"spring.cloud.kubernetes.config.retry.enabled=false",
					"spring.cloud.kubernetes.config.read-type=SINGLE")
			.run(context -> {
				ConfigMapConfigProperties props = context.getBean(ConfigMapConfigProperties.class);
				Assertions.assertThat(props).isNotNull();

				Assertions.assertThat(props.sources().size()).isEqualTo(1);
				ConfigMapConfigProperties.Source source = props.sources().get(0);
				Assertions.assertThat(source.name()).isEqualTo("source-a");
				Assertions.assertThat(source.namespace()).isEqualTo("source-namespace-a");
				Assertions.assertThat(source.labels().size()).isEqualTo(1);
				Assertions.assertThat(source.labels().get("key")).isEqualTo("source-value");
				Assertions.assertThat(source.explicitPrefix()).isEqualTo("source-prefix");
				Assertions.assertThat(source.useNameAsPrefix()).isTrue();
				Assertions.assertThat(source.includeProfileSpecificSources()).isTrue();

				Assertions.assertThat(props.labels().size()).isEqualTo(1);
				Assertions.assertThat(props.labels().get("label-a")).isEqualTo("label-a");

				Assertions.assertThat(props.enabled()).isFalse();
				Assertions.assertThat(props.name()).isEqualTo("name");
				Assertions.assertThat(props.namespace()).isEqualTo("namespace");
				Assertions.assertThat(props.useNameAsPrefix()).isTrue();
				Assertions.assertThat(props.includeProfileSpecificSources()).isTrue();
				Assertions.assertThat(props.failFast()).isTrue();
				Assertions.assertThat(props.readType()).isSameAs(SINGLE);

				RetryProperties retryProperties = props.retry();
				Assertions.assertThat(retryProperties).isNotNull();
				Assertions.assertThat(retryProperties.initialInterval()).isEqualTo(1);
				Assertions.assertThat(retryProperties.multiplier()).isEqualTo(1.2);
				Assertions.assertThat(retryProperties.maxInterval()).isEqualTo(3);
				Assertions.assertThat(retryProperties.enabled()).isFalse();

			});
	}

	@Configuration
	@EnableConfigurationProperties(ConfigMapConfigProperties.class)
	static class Config {

	}

}
