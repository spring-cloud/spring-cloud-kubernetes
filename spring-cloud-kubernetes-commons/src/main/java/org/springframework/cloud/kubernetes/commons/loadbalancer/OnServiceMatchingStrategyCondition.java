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

package org.springframework.cloud.kubernetes.commons.loadbalancer;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.springframework.cloud.kubernetes.commons.loadbalancer.ServiceMatchingStrategy.NAME;

/**
 * @author wind57
 */
class OnServiceMatchingStrategyCondition extends SpringBootCondition {

	private static final String CLASS_NAME = ConditionalOnServiceMatchingStrategyCondition.class.getName();

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {

		// default value is NAME, so if nothing is set we will use that strategy
		ServiceMatchingStrategy fromEnvironment = context.getEnvironment()
			.getProperty("spring.cloud.kubernetes.loadbalancer.service-matching-strategy",
					ServiceMatchingStrategy.class, NAME);

		ServiceMatchingStrategy fromAnnotation = (ServiceMatchingStrategy) metadata.getAnnotationAttributes(CLASS_NAME)
			.get("value");

		if (fromEnvironment.equals(fromAnnotation)) {
			return ConditionOutcome.match("Strategy matched: " + fromAnnotation);
		}
		return ConditionOutcome.noMatch("Strategy did not match.");
	}

}
