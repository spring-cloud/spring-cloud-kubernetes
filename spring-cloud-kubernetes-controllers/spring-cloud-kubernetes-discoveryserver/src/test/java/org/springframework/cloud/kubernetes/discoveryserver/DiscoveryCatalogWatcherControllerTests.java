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

package org.springframework.cloud.kubernetes.discoveryserver;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import org.springframework.cloud.kubernetes.commons.discovery.EndpointNameAndNamespace;

/**
 * @author wind57
 */
class DiscoveryCatalogWatcherControllerTests {

	private final HeartBeatListener heartBeatListener = Mockito.mock(HeartBeatListener.class);

	@Test
	void test() {
		Mockito.when(heartBeatListener.lastState())
				.thenReturn(new AtomicReference<>(List.of(new EndpointNameAndNamespace("one", "two"))));

		DiscoveryCatalogWatcherController catalogWatcherController = new DiscoveryCatalogWatcherController(
				heartBeatListener);

		StepVerifier.create(catalogWatcherController.state())
				.expectNext(List.of(new EndpointNameAndNamespace("one", "two"))).verifyComplete();
	}

}
