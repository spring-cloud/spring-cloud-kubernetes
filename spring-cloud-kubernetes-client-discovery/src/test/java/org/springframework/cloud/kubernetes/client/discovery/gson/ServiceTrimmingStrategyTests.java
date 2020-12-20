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

package org.springframework.cloud.kubernetes.client.discovery.gson;

import java.util.Collections;

import com.google.gson.Gson;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1LoadBalancerStatus;
import io.kubernetes.client.openapi.models.V1ManagedFieldsEntry;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1ServiceStatus;
import org.junit.Assert;
import org.junit.Test;

public class ServiceTrimmingStrategyTests {

	private static final ServiceTrimmingStrategy STRATEGY = new ServiceTrimmingStrategy();

	private static final Gson GSON = new Gson().newBuilder().addDeserializationExclusionStrategy(STRATEGY).create();

	// "V1ObjectMeta" is present and has no "managedFields"
	@Test
	public void testV1ObjectMetaWithManagedFieldsIsSkipped() {
		V1ObjectMeta meta = new V1ObjectMeta().name("foo");
		V1Service service = new V1Service().metadata(meta).kind("service");

		String serializedService = GSON.toJson(service);

		V1Service deserializedService = GSON.fromJson(serializedService, V1Service.class);
		Assert.assertNotNull(deserializedService);
		Assert.assertEquals("service", deserializedService.getKind());

		V1ObjectMeta deserializedMeta = deserializedService.getMetadata();
		Assert.assertNotNull(deserializedMeta);

		Assert.assertEquals("foo", deserializedMeta.getName());
		Assert.assertNull(deserializedMeta.getManagedFields());
	}

	// "V1ObjectMeta" is present and has "managedFields"
	@Test
	public void testV1ObjectMetaWithoutManagedFieldsIsSkipped() {
		V1ObjectMeta meta = new V1ObjectMeta().name("foo")
				.managedFields(Collections.singletonList(new V1ManagedFieldsEntry()));

		V1Service service = new V1Service().metadata(meta).kind("service");

		String serializedService = GSON.toJson(service);

		V1Service deserializedService = GSON.fromJson(serializedService, V1Service.class);
		Assert.assertNotNull(deserializedService);
		Assert.assertEquals("service", deserializedService.getKind());

		V1ObjectMeta deserializedMeta = deserializedService.getMetadata();
		Assert.assertNotNull(deserializedMeta);

		Assert.assertEquals("foo", deserializedMeta.getName());
		Assert.assertNull(deserializedMeta.getManagedFields());
	}

	@Test
	public void testDeserializingService() {

		V1ObjectMeta meta = new V1ObjectMeta().name("foo")
				.managedFields(Collections.singletonList(new V1ManagedFieldsEntry()));

		V1LoadBalancerStatus balancerStatus = new V1LoadBalancerStatus()
				.addIngressItem(new V1LoadBalancerIngress().ip("2.2.2.2"));

		V1ServiceStatus serviceStatus = new V1ServiceStatus().loadBalancer(balancerStatus);
		V1ServiceSpec serviceSpec = new V1ServiceSpec().loadBalancerIP("1.1.1.1");

		V1Service input = new V1Service().metadata(meta).spec(serviceSpec).status(serviceStatus);

		String data = GSON.toJson(input);
		V1Service output = GSON.fromJson(data, V1Service.class);

		// spec should be excluded
		Assert.assertNull(output.getSpec());
		// status should be excluded
		Assert.assertNull(output.getStatus());
		V1ObjectMeta metadata = output.getMetadata();
		Assert.assertNotNull(metadata);
		// managed-fields should be excluded
		Assert.assertNull(metadata.getManagedFields());
	}

}
