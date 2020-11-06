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

import java.util.Arrays;

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

	@Test
	public void testDeserializingService() {
		Gson gson = new Gson().newBuilder().addDeserializationExclusionStrategy(new ServiceTrimmingStrategy()).create();
		V1Service input = new V1Service()
				.metadata(new V1ObjectMeta().name("foo").managedFields(Arrays.asList(new V1ManagedFieldsEntry())))
				.spec(new V1ServiceSpec().loadBalancerIP("1.1.1.1")).status(new V1ServiceStatus().loadBalancer(
						new V1LoadBalancerStatus().addIngressItem(new V1LoadBalancerIngress().ip("2.2.2.2"))));
		String data = gson.toJson(input);
		V1Service output = gson.fromJson(data, V1Service.class);

		// spec should be excluded
		Assert.assertNull(output.getSpec());
		// status should be excluded
		Assert.assertNull(output.getStatus());
		// managed-fields should be excluded
		Assert.assertNull(output.getMetadata().getManagedFields());
	}

}
