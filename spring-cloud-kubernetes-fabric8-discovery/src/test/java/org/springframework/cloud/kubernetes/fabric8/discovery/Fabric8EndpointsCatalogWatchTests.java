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

import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class Fabric8EndpointsCatalogWatchTests {

	private final Fabric8EndpointsCatalogWatch catalogWatch = new Fabric8EndpointsCatalogWatch();

	@Test
	void stateEndpointsWithoutSubsets() {

		// though we set it to null here, the mock client when creating it
		// will set it to an empty list. I will keep it like this, may be client changes
		// in the future and we have the case still covered by a test
		List<EndpointSubset> endpointSubsets = null;

		Endpoints endpoints = new EndpointsBuilder()
			.withMetadata(new ObjectMetaBuilder().withLabels(Map.of()).withName("endpoints-no-subsets").build())
			.withSubsets(endpointSubsets)
			.build();

		// we do not fail, even if Subsets are not present
		List<EndpointNameAndNamespace> result = catalogWatch.state(List.of(endpoints));
		assertThat(result).isEmpty();
	}

}
