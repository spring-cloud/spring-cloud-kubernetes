/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.applications.test_for_1715;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Stubs for this test are in
 * {@link org.springframework.cloud.kubernetes.client.config.bootstrap.stubs.FixFor1715ConfigurationStub}
 *
 * @author wind57
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Fix1715App.class,
		properties = { "spring.cloud.bootstrap.name=fix-1715", "fix.1715.enabled=true",
				"spring.main.cloud-platform=KUBERNETES", "spring.cloud.bootstrap.enabled=true",
				"spring.cloud.kubernetes.client.namespace=spring-k8s" })
public class BootstrapTests extends AbstractTests {

}
