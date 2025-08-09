/*
 * Copyright 2013-2025 the original author or authors.
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

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class Fabric8EndpointSliceV1CatalogWatchTests {

	private final Fabric8EndpointSliceV1CatalogWatch catalogWatch = new Fabric8EndpointSliceV1CatalogWatch();

	@Test
	void stateEndpointsWithoutSubsets() {

		List<Endpoint> endpoints = null;

		EndpointSlice slice = new EndpointSliceBuilder()
			.withMetadata(new ObjectMetaBuilder().withNamespace("default")
				.withName("slice-no-endpoints")
				.withLabels(Map.of())
				.build())
			.withEndpoints(endpoints)
			.build();

		// even if Endpoints are missing, we do not fail
		assertThat(catalogWatch.state(List.of(slice))).isEmpty();
	}

}
