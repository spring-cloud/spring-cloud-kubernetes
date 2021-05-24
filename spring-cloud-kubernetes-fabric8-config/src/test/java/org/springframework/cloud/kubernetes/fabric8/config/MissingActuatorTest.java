/*
 * Copyright 2013-2019 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

// inspired by spring-cloud-commons: RefreshAutoConfigurationMoreClassPathTests

@ExtendWith({ SpringExtension.class, OutputCaptureExtension.class })
@ClassPathExclusions({ "spring-boot-actuator-autoconfigure-*.jar", "spring-boot-starter-actuator-*.jar" })
public class MissingActuatorTest {

	private static ConfigurableApplicationContext getApplicationContext(Class<?> configuration, String... properties) {
		return new SpringApplicationBuilder(configuration).web(WebApplicationType.NONE).properties(properties).run();
	}

	@Test
	public void unknownClassProtected(CapturedOutput capturedOutput) {
		try (ConfigurableApplicationContext context = getApplicationContext(Config.class, "debug=true")) {
			String output = capturedOutput.toString();
			assertThat(output)
					.doesNotContain("Failed to introspect annotations on"
							+ " [class org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration")
					.doesNotContain("TypeNotPresentExceptionProxy");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class Config {

	}

}
