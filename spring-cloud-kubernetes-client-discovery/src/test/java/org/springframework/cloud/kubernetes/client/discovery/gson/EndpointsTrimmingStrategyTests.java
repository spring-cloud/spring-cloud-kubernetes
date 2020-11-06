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
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1ManagedFieldsEntry;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.junit.Assert;
import org.junit.Test;

public class EndpointsTrimmingStrategyTests {

	@Test
	public void testDeserializingEndpoints() {
		Gson gson = new Gson().newBuilder().addDeserializationExclusionStrategy(new EndpointsTrimmingStrategy())
				.create();
		V1Endpoints input = new V1Endpoints()
				.metadata(new V1ObjectMeta().name("foo").managedFields(Arrays.asList(new V1ManagedFieldsEntry())));

		String data = gson.toJson(input);
		V1Endpoints output = gson.fromJson(data, V1Endpoints.class);

		// managed-fields should be excluded
		Assert.assertNull(output.getMetadata().getManagedFields());

	}

}
