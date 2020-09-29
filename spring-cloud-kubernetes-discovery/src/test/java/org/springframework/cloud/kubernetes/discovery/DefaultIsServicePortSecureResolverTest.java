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

package org.springframework.cloud.kubernetes.discovery;

import java.util.HashMap;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultIsServicePortSecureResolverTest {

	@Test
	public void testPortNumbersOnly() {
		final KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties();
		properties.getKnownSecurePorts().add(12345);

		final DefaultIsServicePortSecureResolver sut = new DefaultIsServicePortSecureResolver(properties);

		assertThat(sut.resolve(new DefaultIsServicePortSecureResolver.Input(null, "dummy"))).isFalse();
		assertThat(sut.resolve(new DefaultIsServicePortSecureResolver.Input(8080, "dummy"))).isFalse();
		assertThat(sut.resolve(new DefaultIsServicePortSecureResolver.Input(1234, "dummy"))).isFalse();

		assertThat(sut.resolve(new DefaultIsServicePortSecureResolver.Input(443, "dummy"))).isTrue();
		assertThat(sut.resolve(new DefaultIsServicePortSecureResolver.Input(8443, "dummy"))).isTrue();
		assertThat(sut.resolve(new DefaultIsServicePortSecureResolver.Input(12345, "dummy"))).isTrue();
	}

	@Test
	public void testLabelsAndAnnotations() {
		final DefaultIsServicePortSecureResolver sut = new DefaultIsServicePortSecureResolver(
				new KubernetesDiscoveryProperties());

		assertThat(
				sut.resolve(new DefaultIsServicePortSecureResolver.Input(8080, "dummy", new HashMap<String, String>() {
					{
						put("secured", "true");
						put("other", "value");
					}
				}, new HashMap<>()))).isTrue();
		assertThat(
				sut.resolve(new DefaultIsServicePortSecureResolver.Input(1234, "dummy", new HashMap<String, String>() {
					{
						put("other", "value");
						put("secured", "1");
					}
				}, new HashMap<>()))).isTrue();
		assertThat(sut.resolve(new DefaultIsServicePortSecureResolver.Input(4321, "dummy", new HashMap<>(),
				new HashMap<String, String>() {
					{
						put("other1", "value1");
						put("secured", "yes");
						put("other2", "value2");
					}
				}))).isTrue();
	}

}
