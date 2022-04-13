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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.kubernetes.fabric8.config.example.App;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Charles Moulliard
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = App.class,
		properties = { "spring.application.name=configmap-with-profile-no-active-profiles-example",
				"spring.cloud.kubernetes.reload.enabled=false", "spring.main.cloud-platform=KUBERNETES",
				"spring.config.import=kubernetes:" })
@AutoConfigureWebTestClient
@EnableKubernetesMockClient(crud = true, https = false)
public class ConfigDataConfigMapsWithProfilesNoActiveProfileTests extends ConfigMapsWithProfilesNoActiveProfileTests {

	private static KubernetesClient mockClient;

	@BeforeAll
	public static void setUpBeforeClass() {
		setUpBeforeClass(mockClient);
	}

}
