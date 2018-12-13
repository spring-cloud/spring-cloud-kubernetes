/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.kubernetes.discovery;

import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultIsServicePortSecureResolverTest {


	private static final KubernetesDiscoveryProperties securedNotSetProperties = new KubernetesDiscoveryProperties();
	private static final KubernetesDiscoveryProperties securedSetProperties = new KubernetesDiscoveryProperties();

	static {
		securedSetProperties.setSecured(true);
	}

	@Test
	public void testSecuredSetInPropertiesAlwaysReturnsTrue() {
		final DefaultIsServicePortSecureResolver sut = new DefaultIsServicePortSecureResolver(securedSetProperties);

		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(null, "dummy")));
		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(8080, "dummy")));
		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(8443, "dummy")));
		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(
			8080,
			"dummy",
			new HashMap<String, String>() {{
				put("secured", "false");
			}},
			new HashMap<>()))
		);
	}

	@Test
	public void testPortNumbersOnly() {
		final DefaultIsServicePortSecureResolver sut = new DefaultIsServicePortSecureResolver(securedNotSetProperties);

		assertFalse(sut.resolve(new IsServicePortSecureResolver.Input(null, "dummy")));
		assertFalse(sut.resolve(new IsServicePortSecureResolver.Input(8080, "dummy")));
		assertFalse(sut.resolve(new IsServicePortSecureResolver.Input(1234, "dummy")));

		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(443, "dummy")));
		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(8443, "dummy")));
	}

	@Test
	public void testLabelsAndAnnotations() {
		final DefaultIsServicePortSecureResolver sut = new DefaultIsServicePortSecureResolver(securedNotSetProperties);

		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(
			8080,
			"dummy",
			new HashMap<String, String>() {{
				put("secured", "true");
				put("other", "value");
			}},
			new HashMap<>()))
		);
		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(
			1234,
			"dummy",
			new HashMap<String, String>() {{
				put("other", "value");
				put("secured", "1");
			}},
			new HashMap<>()))
		);
		assertTrue(sut.resolve(new IsServicePortSecureResolver.Input(
			4321,
			"dummy",
			new HashMap<>(),
			new HashMap<String, String>() {{
				put("other1", "value1");
				put("secured", "yes");
				put("other2", "value2");
			}}))
		);
	}
}
