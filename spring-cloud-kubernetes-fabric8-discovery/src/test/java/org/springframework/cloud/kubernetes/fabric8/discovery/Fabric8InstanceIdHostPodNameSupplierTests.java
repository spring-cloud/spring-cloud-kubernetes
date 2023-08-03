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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.kubernetes.commons.discovery.InstanceIdHostPodName;

/**
 * @author wind57
 */
class Fabric8InstanceIdHostPodNameSupplierTests {

	@Test
	void instanceIdNoEndpointAddress() {
		EndpointAddress endpointAddress = null;
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withUid("123").build()).build();

		Fabric8InstanceIdHostPodNameSupplier supplier = new Fabric8InstanceIdHostPodNameSupplier(
			endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.instanceId(), "123");
	}

}
