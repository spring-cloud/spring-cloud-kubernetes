/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.client.reload.it;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.cloud.kubernetes.fabric8.client.reload.App;
import org.testcontainers.k3s.K3sContainer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.kubernetes.integration.tests.commons.Commons;
import org.springframework.cloud.kubernetes.integration.tests.commons.fabric8_client.Util;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = { App.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class Fabric8EventReloadBase {

	protected static final K3sContainer K3S = Commons.container();

	protected static Util util;

	@BeforeAll
	protected static void beforeAll() {
		K3S.start();
		util = new Util(K3S);
	}

}
