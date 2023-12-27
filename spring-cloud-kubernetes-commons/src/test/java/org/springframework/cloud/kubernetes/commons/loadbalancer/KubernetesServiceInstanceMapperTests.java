/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.loadbalancer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author wind57
 */
@ExtendWith(OutputCaptureExtension.class)
class KubernetesServiceInstanceMapperTests {

	private static final String SECURED_LABEL_MESSAGE = "Service has a true 'secured' label";
	private static final String SECURED_ANNOTATION_MESSAGE = "Service has a true 'secured' annotation";
	private static final String NAME_ENDS_IN_HTTPS_MESSAGE = "Service port name ends with 'https'";
	private static final String PORT_ENDS_IN_443_MESSAGE = "Service port ends with '443'";

	@Test
	void testCreateHostWithNamespace() {
		String namespace = "customNamespace";
		String host = KubernetesServiceInstanceMapper.createHost("serviceName", namespace, "clusterDomain");
		assertEquals("serviceName.customNamespace.svc.clusterDomain", host);
	}

	@Test
	void testCreateHostWithEmptyNamespace() {
		String host = KubernetesServiceInstanceMapper.createHost("serviceName", "", "clusterDomain");
		assertEquals("serviceName.default.svc.clusterDomain", host);
	}

	@Test
	void testCreateHostWithNullNamespace() {
		String host = KubernetesServiceInstanceMapper.createHost("serviceName", null, "clusterDomain");
		assertEquals("serviceName.default.svc.clusterDomain", host);
	}

	@Test
	void testIsSecureWithTrueLabel(CapturedOutput output) {
		Map<String, String> labels = Map.of("secured", "true");
		Map<String, String> annotations = Map.of();
		String servicePortName = null;
		Integer servicePort = null;
		assertTrue(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));

		assertTrue(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertFalse(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertFalse(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertFalse(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

	@Test
	void testIsSecureWithTrueAnnotation(CapturedOutput output) {
		// empty labels
		Map<String, String> labels = Map.of();
		Map<String, String> annotations = Map.of("secured", "true");
		String servicePortName = null;
		Integer servicePort = null;
		assertTrue(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));

		assertFalse(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertTrue(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertFalse(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertFalse(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

	@Test
	void testIsSecureWithTrueAnnotationNullLabels(CapturedOutput output) {
		// null labels
		Map<String, String> labels = null;
		Map<String, String> annotations = Map.of("secured", "true");
		String servicePortName = null;
		Integer servicePort = null;
		assertTrue(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));

		assertFalse(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertTrue(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertFalse(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertFalse(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

	@Test
	void testIsNotSecureServicePortNameAndServicePortAreNull(CapturedOutput output) {
		// null labels
		Map<String, String> labels = null;
		// null annotations
		Map<String, String> annotations = null;
		String servicePortName = null;
		Integer servicePort = null;
		assertFalse(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));

		assertFalse(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertFalse(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertFalse(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertFalse(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

	@Test
	void testIsNotSecureServicePortNameDoesNotMatch(CapturedOutput output) {
		// null labels
		Map<String, String> labels = null;
		// null annotations
		Map<String, String> annotations = null;
		String servicePortName = "abc_https_def";
		Integer servicePort = null;
		assertFalse(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));


		assertFalse(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertFalse(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertFalse(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertFalse(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

	@Test
	void testIsSecureServicePortNameMatches(CapturedOutput output) {
		// null labels
		Map<String, String> labels = null;
		// null annotations
		Map<String, String> annotations = null;
		String servicePortName = "abc_https";
		Integer servicePort = null;
		assertTrue(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));

		assertFalse(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertFalse(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertTrue(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertFalse(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

	@Test
	void testIsNotSecureServicePortDoesNotMatch(CapturedOutput output) {
		// null labels
		Map<String, String> labels = null;
		// null annotations
		Map<String, String> annotations = null;
		String servicePortName = null;
		Integer servicePort = 444;
		assertFalse(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));

		assertFalse(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertFalse(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertFalse(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertFalse(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

	@Test
	void testIsSecureServicePortMatches(CapturedOutput output) {
		// null labels
		Map<String, String> labels = null;
		// null annotations
		Map<String, String> annotations = null;
		String servicePortName = null;
		Integer servicePort = 443;
		assertTrue(KubernetesServiceInstanceMapper.isSecure(labels, annotations, servicePortName, servicePort));

		assertFalse(output.getOut().contains(SECURED_LABEL_MESSAGE));
		assertFalse(output.getOut().contains(SECURED_ANNOTATION_MESSAGE));
		assertFalse(output.getOut().contains(NAME_ENDS_IN_HTTPS_MESSAGE));
		assertTrue(output.getOut().contains(PORT_ENDS_IN_443_MESSAGE));
	}

}
