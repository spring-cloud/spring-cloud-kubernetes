/*
 * Copyright 2013-2021 the original author or authors.
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
import org.springframework.cloud.kubernetes.fabric8.config.include_profile_specific_sources.IncludeProfileSpecificSourcesApp;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author wind57
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = IncludeProfileSpecificSourcesApp.class,
		properties = { "spring.cloud.bootstrap.name=include-profile-specific-sources",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true" })
@AutoConfigureWebTestClient
@EnableKubernetesMockClient(crud = true, https = false)
@ActiveProfiles("dev")
class BootstrapConfigMapWithIncludeProfileSpecificSourcesTests extends ConfigMapWithIncludeProfileSpecificSourcesTests {

	private static KubernetesClient mockClient;

	@BeforeAll
	public static void setUpBeforeClass() {
		setUpBeforeClass(mockClient);
	}

}
