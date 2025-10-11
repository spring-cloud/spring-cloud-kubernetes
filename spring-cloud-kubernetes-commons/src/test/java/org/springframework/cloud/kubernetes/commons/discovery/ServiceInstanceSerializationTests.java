/*
 * Copyright 2019-present the original author or authors.
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

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.client.ServiceInstance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author wind57
 */
class ServiceInstanceSerializationTests {

	private static final BasicJsonTester BASIC_JSON_TESTER = new BasicJsonTester(
			ServiceInstanceSerializationTests.class);

	/**
	 * <pre>
	 *     {
	 *   	"instanceId": "instanceId",
	 *   	"serviceId": "serviceId",
	 *   	"host": "host",
	 *   	"port": 8080,
	 *   	"metadata": {
	 *     	"k8s_namespace": "spring-k8s"
	 *   	},
	 *   	"secure": true,
	 *   	"namespace": "namespace",
	 *   	"cluster": "cluster",
	 *   	"podMetadata": {
	 *     	"pod_root": {
	 *       	"pod_key": "pod_value"
	 *     	}
	 *   	},
	 *   	"scheme": "https",
	 *   	"uri": "https://host:8080"
	 * 		}
	 * </pre>
	 */
	@Test
	void defaultKubernetesServiceInstanceSerializationTest() throws JsonProcessingException {

		DefaultKubernetesServiceInstance instance = new DefaultKubernetesServiceInstance("instanceId", "serviceId",
				"host", 8080, Map.of("k8s_namespace", "spring-k8s"), true, "namespace", "cluster",
				Map.of("pod_root", Map.of("pod_key", "pod_value")));

		String serialized = new ObjectMapper().writeValueAsString(instance);
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.instanceId")
			.isEqualTo("instanceId");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.serviceId")
			.isEqualTo("serviceId");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.host").isEqualTo("host");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathNumberValue("$.port").isEqualTo(8080);
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathMapValue("$.metadata")
			.containsExactlyInAnyOrderEntriesOf(Map.of("k8s_namespace", "spring-k8s"));
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathBooleanValue("$.secure").isTrue();
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.namespace")
			.isEqualTo("namespace");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.cluster").isEqualTo("cluster");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathMapValue("$.podMetadata")
			.containsExactlyInAnyOrderEntriesOf(Map.of("pod_root", Map.of("pod_key", "pod_value")));
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.scheme").isEqualTo("https");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.uri")
			.isEqualTo("https://host:8080");
	}

	@Test
	void defaultKubernetesServiceInstanceDeserializationTest() throws JsonProcessingException {

		String serialized = """
			{
	  	"instanceId": "instanceId",
	    	"serviceId": "serviceId",
	    	"host": "host",
	    	"port": 8080,
	    	"metadata": {
	      	"k8s_namespace": "spring-k8s"
	    	},
	    	"secure": true,
	    	"namespace": "namespace",
	    	"cluster": "cluster",
	    	"podMetadata": {
	      	"pod_root": {
	        	"pod_key": "pod_value"
	      	}
	    	},
	    	"scheme": "https",
	    	"uri": "https://host:8080"
	  		}
			""";

		ServiceInstance deserialized =
			new ObjectMapper().readValue(serialized, DefaultKubernetesServiceInstance.class);
		assertThat(deserialized.getInstanceId()).isEqualTo("instanceId");
		assertThat(deserialized.getServiceId()).isEqualTo("serviceId");
		assertThat(deserialized.getScheme()).isEqualTo("https");
		assertThat(deserialized.getHost()).isEqualTo("host");
		assertThat(deserialized.getPort()).isEqualTo(8080);
		assertThat(deserialized.getUri().toASCIIString()).isEqualTo("https://host:8080");
		assertThat(deserialized.getMetadata()).containsExactlyInAnyOrderEntriesOf(
			Map.of("k8s_namespace", "spring-k8s")
		);
	}

	@Test
	void externalNameServiceInstanceSerializationTest() throws JsonProcessingException {
		KubernetesExternalNameServiceInstance instance = new KubernetesExternalNameServiceInstance("serviceId", "host",
				"instanceId", Map.of("a", "b"));
		String serialized = new ObjectMapper().writeValueAsString(instance);

		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.serviceId")
			.isEqualTo("serviceId");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.host").isEqualTo("host");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathStringValue("$.instanceId")
			.isEqualTo("instanceId");
		assertThat(BASIC_JSON_TESTER.from(serialized)).extractingJsonPathMapValue("$.metadata")
			.containsExactlyInAnyOrderEntriesOf(Map.of("a", "b"));
	}

}
