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

package org.springframework.cloud.kubernetes.commons;

import java.util.Collection;

import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.cloud.kubernetes.commons.config.SecretsPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.SanitizableData")
@ConditionalOnSanitizeSecrets
class KubernetesCommonsSanitizeAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	SanitizingFunction secretsPropertySourceSanitizingFunction() {
		return data -> {
			PropertySource<?> propertySource = data.getPropertySource();
			if (propertySource instanceof BootstrapPropertySource<?> bootstrapPropertySource) {
				PropertySource<?> source = bootstrapPropertySource.getDelegate();
				if (source instanceof SecretsPropertySource) {
					return new SanitizableData(propertySource, data.getKey(), data.getValue())
						.withValue(SanitizableData.SANITIZED_VALUE);
				}
			}

			if (propertySource instanceof SecretsPropertySource) {
				return new SanitizableData(propertySource, data.getKey(), data.getValue())
					.withValue(SanitizableData.SANITIZED_VALUE);
			}

			// at the moment, our structure is pretty simply, CompositePropertySource
			// children can be SecretsPropertySource; i.e.: there is no recursion
			// needed to get all children. If this structure changes, there are enough
			// unit tests that will start failing.
			if (propertySource instanceof CompositePropertySource compositePropertySource) {
				Collection<PropertySource<?>> sources = compositePropertySource.getPropertySources();
				for (PropertySource<?> one : sources) {
					if (one.containsProperty(data.getKey()) && one instanceof SecretsPropertySource) {
						return new SanitizableData(propertySource, data.getKey(), data.getValue())
							.withValue(SanitizableData.SANITIZED_VALUE);
					}
				}
			}
			return data;
		};
	}

}
