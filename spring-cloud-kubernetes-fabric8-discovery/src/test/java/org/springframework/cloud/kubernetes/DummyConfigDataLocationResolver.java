/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.kubernetes;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.kubernetes.commons.KubernetesNamespaceProvider;
import org.springframework.cloud.kubernetes.commons.config.KubernetesConfigDataLocationResolver;

/**
 * @author wind57
 */
@ConditionalOnProperty(value = "dummy.config.loader.enabled", havingValue = "true", matchIfMissing = false)
class DummyConfigDataLocationResolver extends KubernetesConfigDataLocationResolver {

	DummyConfigDataLocationResolver(DeferredLogFactory factory) {
		super(factory);
	}

	@Override
	protected void registerBeans(ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location,
			Profiles profiles, PropertyHolder propertyHolder, KubernetesNamespaceProvider namespaceProvider) {

	}

}
