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

package org.springframework.cloud.kubernetes.client.discovery;

import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.kubernetes.commons.discovery.InstanceIdHostPodName;

/**
 * @author wind57
 */
class K8sInstanceIdHostPodNameSupplierTests {

	@Test
	void instanceIdNoEndpointAddress() {
		V1Service service = new V1Service().spec(new V1ServiceSpec())
			.metadata(new V1ObjectMeta().uid("123"));

		K8sInstanceIdHostPodNameSupplier supplier = K8sInstanceIdHostPodNameSupplier.externalName(service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.instanceId(), "123");
	}

	@Test
	void instanceIdWithEndpointAddress() {
		V1EndpointAddress endpointAddress = new V1EndpointAddress()
			.targetRef(new V1ObjectReference().uid("456"));
		V1Service service = new V1Service().spec(new V1ServiceSpec())
			.metadata(new V1ObjectMeta().uid("123"));

		K8sInstanceIdHostPodNameSupplier supplier = K8sInstanceIdHostPodNameSupplier
			.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.instanceId(), "456");
	}

	@Test
	void hostNoEndpointAddress() {
		V1Service service = new V1Service()
			.spec(new V1ServiceSpec().externalName("external-name"))
			.metadata(new V1ObjectMeta());

		K8sInstanceIdHostPodNameSupplier supplier = K8sInstanceIdHostPodNameSupplier.externalName(service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.host(), "external-name");
	}

	@Test
	void hostWithEndpointAddress() {
		V1EndpointAddress endpointAddress = new V1EndpointAddress().ip("127.0.0.1");
		V1Service service = new V1Service()
			.spec(new V1ServiceSpec().externalName("external-name"))
			.metadata(new V1ObjectMeta());

		K8sInstanceIdHostPodNameSupplier supplier = K8sInstanceIdHostPodNameSupplier
			.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.host(), "127.0.0.1");
	}

	@Test
	void testPodNameIsNull() {
		V1Service service = new V1Service().metadata(new V1ObjectMeta().uid("123"))
			.spec(new V1ServiceSpec().externalName("external-name"));
		K8sInstanceIdHostPodNameSupplier supplier = K8sInstanceIdHostPodNameSupplier.externalName(service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertNull(result.podName());
	}

	@Test
	void podNameKindNotPod() {
		V1EndpointAddress endpointAddress = new V1EndpointAddress()
			.targetRef(new V1ObjectReference().kind("Service"));
		V1Service service = new V1Service()
			.spec(new V1ServiceSpec().externalName("external-name"))
			.metadata(new V1ObjectMeta());

		K8sInstanceIdHostPodNameSupplier supplier = K8sInstanceIdHostPodNameSupplier
			.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertNull(result.podName());
	}

	@Test
	void podNameKindIsPod() {
		V1EndpointAddress endpointAddress = new V1EndpointAddress()
			.targetRef(new V1ObjectReference().kind("Pod").name("my-pod"));
		V1Service service = new V1Service()
			.spec(new V1ServiceSpec().externalName("external-name"))
			.metadata(new V1ObjectMeta());

		K8sInstanceIdHostPodNameSupplier supplier = K8sInstanceIdHostPodNameSupplier
			.nonExternalName(endpointAddress, service);
		InstanceIdHostPodName result = supplier.get();

		Assertions.assertNotNull(result);
		Assertions.assertEquals(result.podName(), "my-pod");
	}

}
