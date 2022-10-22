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

package org.springframework.cloud.kubernetes.fabric8.discovery;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class ServicePortSecureResolverTest {

	private static final Map<String, String> SECURED_TRUE_MAP = Collections.singletonMap("secured", "true");

	private static final Map<String, String> SECURED_1_MAP = Collections.singletonMap("secured", "1");

	private static final Map<String, String> SECURED_YES_MAP = Collections.singletonMap("secured", "yes");

	private static final Map<String, String> SECURED_ON_MAP = Collections.singletonMap("secured", "on");

	private static final ServicePortSecureResolver.Input SECURED_TRUE = new ServicePortSecureResolver.Input(8080,
			"dummy", SECURED_TRUE_MAP, Collections.emptyMap());

	private static final ServicePortSecureResolver.Input SECURED_1 = new ServicePortSecureResolver.Input(1234, "dummy",
			SECURED_1_MAP, Collections.emptyMap());

	private static final ServicePortSecureResolver.Input SECURED_YES = new ServicePortSecureResolver.Input(4321,
			"dummy", SECURED_YES_MAP, Collections.emptyMap());

	private static final ServicePortSecureResolver.Input SECURED_ON = new ServicePortSecureResolver.Input(4321, "dummy",
			SECURED_ON_MAP, Collections.emptyMap());

	@Test
	public void testPortNumbersOnly() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, true, 60, false, null,
				Set.of(443, 8443, 12345), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0);

		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);

		assertThat(secureResolver.resolve(new ServicePortSecureResolver.Input(null, "dummy"))).isFalse();
		assertThat(secureResolver.resolve(new ServicePortSecureResolver.Input(8080, "dummy"))).isFalse();
		assertThat(secureResolver.resolve(new ServicePortSecureResolver.Input(1234, "dummy"))).isFalse();

		assertThat(secureResolver.resolve(new ServicePortSecureResolver.Input(443, "dummy"))).isTrue();
		assertThat(secureResolver.resolve(new ServicePortSecureResolver.Input(8443, "dummy"))).isTrue();
		assertThat(secureResolver.resolve(new ServicePortSecureResolver.Input(12345, "dummy"))).isTrue();
	}

	@Test
	public void testLabelsAndAnnotations() {
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT);

		assertThat(secureResolver.resolve(SECURED_TRUE)).isTrue();
		assertThat(secureResolver.resolve(SECURED_1)).isTrue();
		assertThat(secureResolver.resolve(SECURED_YES)).isTrue();
		assertThat(secureResolver.resolve(SECURED_ON)).isTrue();
	}

}
