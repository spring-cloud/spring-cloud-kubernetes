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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.Set;

import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.log.LogAccessor;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Conditional that checks if our discovery is based on selective namespaces, i.e.:
 * 'spring.cloud.kubernetes.discovery.namespaces' is set.
 *
 * @author wind57
 */
public final class ConditionalOnSelectiveNamespacesPresent implements ConfigurationCondition {

	private static final LogAccessor LOG = new LogAccessor(
			LogFactory.getLog(ConditionalOnSelectiveNamespacesPresent.class));

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {

		Set<String> selectiveNamespaces = Binder.get(context.getEnvironment())
				.bind("spring.cloud.kubernetes.discovery.namespaces", Bindable.setOf(String.class)).orElse(Set.of());
		boolean selectiveNamespacesPresent = !selectiveNamespaces.isEmpty();
		if (selectiveNamespacesPresent) {
			LOG.debug(() -> "found selective namespaces : " + selectiveNamespaces.stream().sorted().toList());
		}
		else {
			LOG.debug(() -> "selective namespaces not present");
		}
		return selectiveNamespacesPresent;
	}

	@Override
	public ConfigurationCondition.ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.REGISTER_BEAN;
	}

}
