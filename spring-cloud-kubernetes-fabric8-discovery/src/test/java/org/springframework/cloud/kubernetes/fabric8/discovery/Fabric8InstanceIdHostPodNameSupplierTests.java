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
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.InstanceIdHostPodName;

/**
 * @author wind57
 */
class Fabric8InstanceIdHostPodNameSupplierTests {

	@Test
	void instanceIdNoEndpointAddress() {
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().build())
				.withMetadata(new ObjectMetaBuilder().withUid("123").build()).build();

		Fabric8InstanceIdHostPodNameSupplier supplier = Fabric8InstanceIdHostPodNameSupplier.externalName(service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.instanceId(), "123");
	}

	@Test
	void instanceIdWithEndpointAddress() {
		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withUid("456").build()).build();
		Service service = new ServiceBuilder().withSpec(new ServiceSpecBuilder().build())
				.withMetadata(new ObjectMetaBuilder().withUid("123").build()).build();

		Fabric8InstanceIdHostPodNameSupplier supplier = Fabric8InstanceIdHostPodNameSupplier
				.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.instanceId(), "456");
	}

	@Test
	void hostNoEndpointAddress() {
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withExternalName("external-name").build())
				.withMetadata(new ObjectMeta()).build();

		Fabric8InstanceIdHostPodNameSupplier supplier = Fabric8InstanceIdHostPodNameSupplier.externalName(service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.host(), "external-name");
	}

	@Test
	void hostWithEndpointAddress() {
		EndpointAddress endpointAddress = new EndpointAddressBuilder().withIp("127.0.0.1").build();
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withExternalName("external-name").build())
				.withMetadata(new ObjectMeta()).build();

		Fabric8InstanceIdHostPodNameSupplier supplier = Fabric8InstanceIdHostPodNameSupplier
				.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.host(), "127.0.0.1");
	}

	@Test
	void testPodNameIsNull() {
		Service service = new ServiceBuilder().withMetadata(new ObjectMetaBuilder().withUid("123").build())
				.withSpec(new ServiceSpecBuilder().withExternalName("external-name").build()).build();
		Fabric8InstanceIdHostPodNameSupplier supplier = Fabric8InstanceIdHostPodNameSupplier.externalName(service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertNull(result.podName());
	}

	@Test
	void podNameKindNotPod() {
		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withKind("Service").build()).build();
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withExternalName("external-name").build())
				.withMetadata(new ObjectMeta()).build();

		Fabric8InstanceIdHostPodNameSupplier supplier = Fabric8InstanceIdHostPodNameSupplier
				.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertNull(result.podName());
	}

	@Test
	void podNameKindIsPod() {
		EndpointAddress endpointAddress = new EndpointAddressBuilder()
				.withTargetRef(new ObjectReferenceBuilder().withKind("Pod").withName("my-pod").build()).build();
		Service service = new ServiceBuilder()
				.withSpec(new ServiceSpecBuilder().withExternalName("external-name").build())
				.withMetadata(new ObjectMeta()).build();

		Fabric8InstanceIdHostPodNameSupplier supplier = Fabric8InstanceIdHostPodNameSupplier
				.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.podName(), "my-pod");
	}

}
