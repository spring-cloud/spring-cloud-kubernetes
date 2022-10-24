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

package org.springframework.cloud.kubernetes.commons;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class KubernetesClientPropertiesTests {

	@Test
	void testDefaults() {
		new ApplicationContextRunner()
			.withUserConfiguration(Config.class)
			.run(context -> {
				KubernetesClientProperties properties = context.getBean(KubernetesClientProperties.class);
				assertThat(properties).isNotNull();
				assertThat(properties.trustCerts()).isNull();
				assertThat(properties.masterUrl()).isNull();
				assertThat(properties.apiVersion()).isNull();
				assertThat(properties.namespace()).isNull();
				assertThat(properties.caCertFile()).isNull();
				assertThat(properties.caCertData()).isNull();
				assertThat(properties.clientCertData()).isNull();
				assertThat(properties.clientKeyFile()).isNull();
				assertThat(properties.clientKeyData()).isNull();
				assertThat(properties.clientKeyPassphrase()).isNull();
				assertThat(properties.username()).isNull();
				assertThat(properties.password()).isNull();
				assertThat(properties.watchReconnectInterval()).isNull();
				assertThat(properties.watchReconnectLimit()).isNull();
				assertThat(properties.connectionTimeout()).isNull();
				assertThat(properties.requestTimeout()).isNull();
				assertThat(properties.rollingTimeout()).isNull();
				assertThat(properties.loggingInterval()).isNull();
				assertThat(properties.httpProxy()).isNull();
				assertThat(properties.httpsProxy()).isNull();
				assertThat(properties.proxyUsername()).isNull();
				assertThat(properties.proxyPassword()).isNull();
				assertThat(properties.oauthToken()).isNull();
				assertThat(properties.noProxy()).isNull();
				assertThat(properties.serviceAccountNamespacePath()).isNull();
				assertThat(properties.userAgent()).isNull();
			});
	}

	@EnableConfigurationProperties(KubernetesClientProperties.class)
	@Configuration
	static class Config {

	}

}
