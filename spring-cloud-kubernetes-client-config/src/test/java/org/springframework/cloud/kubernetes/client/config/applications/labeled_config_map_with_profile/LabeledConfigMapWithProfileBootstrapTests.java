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

package org.springframework.cloud.kubernetes.client.config.applications.labeled_config_map_with_profile;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * @author wind57
 */
@ActiveProfiles({ "k8s", "prod" })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		classes = LabeledConfigMapWithProfileApp.class,
		properties = { "spring.cloud.bootstrap.name=labeled-configmap-with-profile",
				"labeled.config.map.with.profile.stub=true", "spring.main.cloud-platform=KUBERNETES",
				"spring.cloud.bootstrap.enabled=true", "spring.cloud.kubernetes.client.namespace=spring-k8s" })
class LabeledConfigMapWithProfileBootstrapTests extends LabeledConfigMapWithProfileTests {

}
