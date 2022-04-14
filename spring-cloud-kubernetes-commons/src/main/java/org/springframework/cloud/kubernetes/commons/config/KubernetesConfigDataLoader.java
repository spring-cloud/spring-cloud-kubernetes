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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigData.Option;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * @author Ryan Baxter
 */
public class KubernetesConfigDataLoader implements ConfigDataLoader<KubernetesConfigDataResource>, Ordered {

	@Override
	public ConfigData load(ConfigDataLoaderContext context, KubernetesConfigDataResource resource)
			throws IOException, ConfigDataResourceNotFoundException {

		List<PropertySource<?>> propertySources = new ArrayList<>(2);
		ConfigurableBootstrapContext bootstrapContext = context.getBootstrapContext();
		Environment env = resource.getEnvironment();

		if (bootstrapContext.isRegistered(ConfigMapPropertySourceLocator.class)) {
			propertySources.add(bootstrapContext.get(ConfigMapPropertySourceLocator.class).locate(env));
		}
		if (bootstrapContext.isRegistered(SecretsPropertySourceLocator.class)) {
			propertySources.add(bootstrapContext.get(SecretsPropertySourceLocator.class).locate(env));
		}

		// boot 2.4.5+
		return new ConfigData(propertySources, propertySource -> {
			String propertySourceName = propertySource.getName();
			List<Option> options = new ArrayList<>();
			options.add(Option.IGNORE_IMPORTS);
			options.add(Option.IGNORE_PROFILES);
			// TODO: the profile is now available on the backend
			// in a future minor, add the profile associated with a
			// PropertySource see
			// https://github.com/spring-cloud/spring-cloud-config/issues/1874
			for (String profile : resource.getAcceptedProfiles()) {
				// TODO: switch to match
				// , is used as a profile-separator for property sources
				// from vault
				// - is the default profile-separator for property sources
				if (propertySourceName.matches(".*[-,]" + profile + ".*")) {
					// // TODO: switch to Options.with() when implemented
					options.add(Option.PROFILE_SPECIFIC);
				}
			}
			return ConfigData.Options.of(options.toArray(new Option[0]));
		});

	}

	@Override
	public int getOrder() {
		return -1;
	}

}
