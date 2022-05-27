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

package org.springframework.cloud.kubernetes.fabric8.config;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.cloud.kubernetes.commons.config.ConfigUtils;
import org.springframework.cloud.kubernetes.commons.config.NamedSecretNormalizedSource;
import org.springframework.cloud.kubernetes.commons.config.PrefixContext;
import org.springframework.cloud.kubernetes.commons.config.SourceData;

import static org.springframework.cloud.kubernetes.commons.config.ConfigUtils.onException;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.configMapDataByName;
import static org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils.secretDataByName;

/**
 * Provides an implementation of {@link Fabric8ContextToSourceData} for a named secret.
 *
 * @author wind57
 */
final class NamedSecretContextToSourceDataProvider implements Supplier<Fabric8ContextToSourceData> {

	NamedSecretContextToSourceDataProvider() {
	}

	@Override
	public Fabric8ContextToSourceData get() {
		return context -> {

			NamedSecretNormalizedSource source = (NamedSecretNormalizedSource) context.normalizedSource();
			Set<String> propertySourceNames = new LinkedHashSet<>();
			propertySourceNames.add(source.name().orElseThrow());

			Map<String, Object> result = new HashMap<>();
			// error should never be thrown here, since we always expect a name
			// explicit or implicit
			String initialSecretName = source.name().orElseThrow();
			String currentSecretName;
			String namespace = context.namespace();

			try {

				Map<String, Object> data = secretDataByName(context.client(), namespace, initialSecretName);
				result.putAll(data);

				if (context.environment() != null && source.profileSpecificSources()) {
					for (String activeProfile : context.environment().getActiveProfiles()) {
						currentSecretName = initialSecretName + "-" + activeProfile;
						Map<String, String> dataWithProfile = configMapDataByName(context.client(), namespace,
								currentSecretName);
						if (!dataWithProfile.isEmpty()) {
							propertySourceNames.add(currentSecretName);
							result.putAll(dataWithProfile);
						}
					}
				}

				if (source.prefix() != ConfigUtils.Prefix.DEFAULT) {
					// since we are in a named source, calling get on the supplier is
					// safe
					String prefix = source.prefix().prefixProvider().get();
					PrefixContext prefixContext = new PrefixContext(result, prefix, namespace, propertySourceNames);
					return ConfigUtils.withPrefix(source.target(), prefixContext);
				}

			}
			catch (Exception e) {
				String message = "Unable to read Secret with name '" + initialSecretName + "' in namespace '"
						+ namespace + "'";
				onException(source.failFast(), message, e);
			}

			String sourceName = ConfigUtils.sourceName(source.target(), initialSecretName, namespace);
			return new SourceData(sourceName, result);
		};
	}

}
