/*
 * Copyright 2012-2019 the original author or authors.
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

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.bootstrap.BootstrapConfiguration;
import org.springframework.cloud.kubernetes.commons.config.reload.ConfigReloadPropertiesAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.config.reload.Fabric8ConfigReloadAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author Haytham Mohamed
 **/
public class KubernetesConfigTestBase {

	private ConfigurableApplicationContext context;

	protected String[] commonProperties = new String[0];

	protected ConfigurableApplicationContext getContext() {
		return context;
	}

	protected void setup(Class<?> mockClientConfiguration, String... env) {
		String[] properties = Stream.concat(Arrays.stream(commonProperties), Arrays.stream(env)).toArray(String[]::new);
		context = new SpringApplicationBuilder(PropertyPlaceholderAutoConfiguration.class, mockClientConfiguration,
				BootstrapConfiguration.class, Fabric8ConfigReloadAutoConfiguration.class,
				ConfigReloadPropertiesAutoConfiguration.class, RefreshAutoConfiguration.class)
						.web(WebApplicationType.NONE).properties(properties).run();
	}

	@AfterEach
	public void close() {
		if (context != null) {
			context.close();
		}
	}

}
