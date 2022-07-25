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

package org.springframework.cloud.kubernetes.fabric8.config.named_secret_with_strict;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * @author wind57
 */
@ActiveProfiles({"dev", "us-west", "a", "b", "c", "d"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = NamedSecretWithStrictApp.class,
		properties = { "spring.application.name=named-secret-with-strict", "spring.main.cloud-platform=KUBERNETES",
				"spring.config.import=kubernetes:,classpath:./named-secret-with-strict.yaml" })
@EnableKubernetesMockClient(crud = true, https = false)
class NamedSecretWithStrictConfigDataTests extends NamedSecretWithStrictTests {

	private static KubernetesClient mockClient;

	@BeforeAll
	static void setUpBeforeClass() {
		setUpBeforeClass(mockClient);
	}

}
