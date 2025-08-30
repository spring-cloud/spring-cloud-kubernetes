/*
 * Copyright 2012-present the original author or authors.
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
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

/**
 * @author wind57
 */
class Fabric8CatalogWatchContextTests {

	@Test
	void stateWithASingleElementNameNotNull() {

		Stream<ObjectReference> referenceStream = Stream
			.of(new ObjectReferenceBuilder().withName("a").withNamespace("default").build());

		List<EndpointNameAndNamespace> result = Fabric8CatalogWatchContext.state(referenceStream);
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).endpointName()).isEqualTo("a");
		Assertions.assertThat(result.get(0).namespace()).isEqualTo("default");

	}

	@Test
	void stateWithASingleElementNameNull() {

		Stream<ObjectReference> referenceStream = Stream
			.of(new ObjectReferenceBuilder().withName(null).withNamespace("default").build());

		List<EndpointNameAndNamespace> result = Fabric8CatalogWatchContext.state(referenceStream);
		Assertions.assertThat(result.size()).isEqualTo(1);
		Assertions.assertThat(result.get(0).endpointName()).isNull();
		Assertions.assertThat(result.get(0).namespace()).isEqualTo("default");

	}

	@Test
	void stateWithTwoElementsNameNull() {

		Stream<ObjectReference> referenceStream = Stream.of(
				new ObjectReferenceBuilder().withName(null).withNamespace("defaultNull").build(),
				new ObjectReferenceBuilder().withName("a").withNamespace("defaultA").build());

		List<EndpointNameAndNamespace> result = Fabric8CatalogWatchContext.state(referenceStream);
		Assertions.assertThat(result.size()).isEqualTo(2);
		Assertions.assertThat(result.get(0).endpointName()).isEqualTo("a");
		Assertions.assertThat(result.get(0).namespace()).isEqualTo("defaultA");
		Assertions.assertThat(result.get(1).endpointName()).isNull();
		Assertions.assertThat(result.get(1).namespace()).isEqualTo("defaultNull");

	}

}
