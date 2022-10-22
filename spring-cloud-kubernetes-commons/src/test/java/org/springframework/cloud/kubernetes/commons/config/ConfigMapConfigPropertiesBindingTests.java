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
 * tests that prove that binding works. We need these because we moved to a record
 * for configuration properties.
 */
class ConfigMapConfigPropertiesBindingTests {

	@Test
	void testWithDefaults() {
		new ApplicationContextRunner()
			.withUserConfiguration(Config.class)
			.run(context -> {
				ConfigMapConfigProperties props = context.getBean(ConfigMapConfigProperties.class);
				Assertions.assertNotNull(props);
				Assertions.assertTrue(props.enableApi());
			});
	}

	@Configuration
	@EnableConfigurationProperties(ConfigMapConfigProperties.class)
	static class Config {

	}

}
