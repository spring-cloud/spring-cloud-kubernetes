/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.kubernetes.commons.discovery.ServicePortSecureResolver.Input;

@ExtendWith(OutputCaptureExtension.class)
class ServicePortSecureResolverTest {

	private static final Map<String, String> SECURED_TRUE_MAP = Collections.singletonMap("secured", "true");

	private static final Map<String, String> SECURED_1_MAP = Collections.singletonMap("secured", "1");

	private static final Map<String, String> SECURED_YES_MAP = Collections.singletonMap("secured", "yes");

	private static final Map<String, String> SECURED_ON_MAP = Collections.singletonMap("secured", "on");

	private static final ServicePortSecureResolver.Input SECURED_TRUE = new ServicePortSecureResolver.Input(
			new ServicePortNameAndNumber(8080, "http"), "dummy", SECURED_TRUE_MAP, Collections.emptyMap());

	private static final ServicePortSecureResolver.Input SECURED_1 = new ServicePortSecureResolver.Input(
			new ServicePortNameAndNumber(1234, "http"), "dummy", SECURED_1_MAP, Collections.emptyMap());

	private static final ServicePortSecureResolver.Input SECURED_YES = new ServicePortSecureResolver.Input(
			new ServicePortNameAndNumber(4321, "http"), "dummy", SECURED_YES_MAP, Collections.emptyMap());

	private static final ServicePortSecureResolver.Input SECURED_ON = new ServicePortSecureResolver.Input(
			new ServicePortNameAndNumber(4321, "http"), "dummy", SECURED_ON_MAP, Collections.emptyMap());

	@Test
	void testPortNumbersOnly() {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443, 12345), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
				0, true);

		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);

		assertThat(secureResolver
				.resolve(new Input(new ServicePortNameAndNumber(-1, "http"), "dummy", Map.of(), Map.of()))).isFalse();
		assertThat(secureResolver
				.resolve(new Input(new ServicePortNameAndNumber(8080, "http"), "dummy", Map.of(), Map.of()))).isFalse();
		assertThat(secureResolver
				.resolve(new Input(new ServicePortNameAndNumber(1234, "http"), "dummy", Map.of(), Map.of()))).isFalse();
		assertThat(secureResolver
				.resolve(new Input(new ServicePortNameAndNumber(443, "http"), "dummy", Map.of(), Map.of()))).isTrue();
		assertThat(secureResolver
				.resolve(new Input(new ServicePortNameAndNumber(8443, "http"), "dummy", Map.of(), Map.of()))).isTrue();
		assertThat(secureResolver
				.resolve(new Input(new ServicePortNameAndNumber(12345, "http"), "dummy", Map.of(), Map.of()))).isTrue();
	}

	@Test
	void testLabelsAndAnnotations() {
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(KubernetesDiscoveryProperties.DEFAULT);

		assertThat(secureResolver.resolve(SECURED_TRUE)).isTrue();
		assertThat(secureResolver.resolve(SECURED_1)).isTrue();
		assertThat(secureResolver.resolve(SECURED_YES)).isTrue();
		assertThat(secureResolver.resolve(SECURED_ON)).isTrue();
	}

	@Test
	void testNoneConditionsMet(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443, 12345), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT,
				0, true);
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);
		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		Input input = new Input(portData, "dummy", Map.of(), Map.of());

		boolean result = secureResolver.resolve(input);
		assertThat(result).isFalse();
		assertThat(output.getOut()).isEmpty();
	}

	@Test
	void securedLabelPresentWrongValue(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);
		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		Input input = new Input(portData, "dummy", Map.of("secured", "right"), Map.of());

		boolean result = secureResolver.resolve(input);
		assertThat(result).isFalse();
		assertThat(output.getOut()).isEmpty();
	}

	@Test
	void securedLabelPresentCorrectValue(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);
		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		Input input = new Input(portData, "dummy", Map.of("secured", "true"), Map.of());

		boolean result = secureResolver.resolve(input);
		assertThat(result).isTrue();
		assertThat(output.getOut()).contains(
				"Considering service with name: dummy and port 8080 to be secure since the service contains a true value for the 'secured' label");
	}

	@Test
	void securedAnnotationPresentWrongValue(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);
		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		Input input = new Input(portData, "dummy", Map.of(), Map.of("secured", "right"));

		boolean result = secureResolver.resolve(input);
		assertThat(result).isFalse();
		assertThat(output.getOut()).isEmpty();
	}

	@Test
	void securedAnnotationPresentCorrectValue(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(443, 8443), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0,
				true);
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);
		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		Input input = new Input(portData, "dummy", Map.of(), Map.of("secured", "true"));

		boolean result = secureResolver.resolve(input);
		assertThat(result).isTrue();
		assertThat(output.getOut()).contains(
				"Considering service with name: dummy and port 8080 to be secure since the service contains a true value for the 'secured' annotation");
	}

	@Test
	void knownPortsMatches(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(8080), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);
		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "http");
		Input input = new Input(portData, "dummy", Map.of(), Map.of());

		boolean result = secureResolver.resolve(input);
		assertThat(result).isTrue();
		assertThat(output.getOut()).contains(
				"Considering service with name: dummy and port 8080 to be secure since port is known to be a https port");
	}

	@Test
	void noConditionsMatchButPortIsHttps(CapturedOutput output) {
		KubernetesDiscoveryProperties properties = new KubernetesDiscoveryProperties(true, true, Set.of(), true, 60,
				false, null, Set.of(8081), Map.of(), null, KubernetesDiscoveryProperties.Metadata.DEFAULT, 0, true);
		ServicePortSecureResolver secureResolver = new ServicePortSecureResolver(properties);
		ServicePortNameAndNumber portData = new ServicePortNameAndNumber(8080, "https");
		Input input = new Input(portData, "dummy", Map.of(), Map.of());

		boolean result = secureResolver.resolve(input);
		assertThat(result).isTrue();
		assertThat(output.getOut())
				.contains("Considering service with name: dummy and port 8080 to be secure since port-name is 'https'");
	}

}
