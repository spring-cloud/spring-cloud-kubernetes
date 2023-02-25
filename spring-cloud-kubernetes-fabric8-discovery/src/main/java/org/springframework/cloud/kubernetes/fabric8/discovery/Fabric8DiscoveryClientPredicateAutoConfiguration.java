/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.api.model.Service;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.cloud.client.ConditionalOnBlockingDiscoveryEnabled;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.ConditionalOnKubernetesDiscoveryEnabled;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryPropertiesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author wind57
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@ConditionalOnBlockingOrReactiveEnabled
@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@AutoConfigureAfter(KubernetesDiscoveryPropertiesAutoConfiguration.class)
class Fabric8DiscoveryClientPredicateAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Predicate<Service> predicate(KubernetesDiscoveryProperties properties) {
		SpelExpressionParser parser = new SpelExpressionParser();
		SimpleEvaluationContext evaluationContext = SimpleEvaluationContext.forReadOnlyDataBinding()
			.withInstanceMethods().build();

		String spelExpression = properties.filter();
		Predicate<Service> predicate;
		if (spelExpression == null || spelExpression.isEmpty()) {
			predicate = service -> true;
		}
		else {
			Expression filterExpr = parser.parseExpression(spelExpression);
			predicate = service -> {
				Boolean include = filterExpr.getValue(evaluationContext, service, Boolean.class);
				return Optional.ofNullable(include).orElse(false);
			};
		}
		return predicate;
	}

}
