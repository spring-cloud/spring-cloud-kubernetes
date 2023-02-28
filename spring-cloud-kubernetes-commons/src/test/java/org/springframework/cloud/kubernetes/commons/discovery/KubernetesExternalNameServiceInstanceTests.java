/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wind57
 */
class KubernetesExternalNameServiceInstanceTests {

	@Test
	void testFields() {
		KubernetesExternalNameServiceInstance instance = new KubernetesExternalNameServiceInstance("123", "spring.io",
				"456", Map.of("a", "b"));
		Assertions.assertEquals(instance.getInstanceId(), "456");
		Assertions.assertEquals(instance.getServiceId(), "123");
		Assertions.assertEquals(instance.getHost(), "spring.io");
		Assertions.assertEquals(instance.getPort(), -1);
		Assertions.assertEquals(instance.getMetadata(), Map.of("a", "b"));
		Assertions.assertNull(instance.getScheme());
		Assertions.assertEquals(instance.getUri().toASCIIString(), "spring.io");
		Assertions.assertEquals(instance.type(), "ExternalName");
	}

}
