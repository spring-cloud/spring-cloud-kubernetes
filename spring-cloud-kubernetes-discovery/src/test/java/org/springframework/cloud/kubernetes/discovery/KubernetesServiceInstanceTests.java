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

package org.springframework.cloud.kubernetes.discovery;

import java.util.Collections;

import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesServiceInstanceTests {

	@Test
	public void schemeIsHttp() {
		assertServiceInstance(false);
	}

	private KubernetesServiceInstance assertServiceInstance(boolean secure) {
		EndpointAddress address = new EndpointAddress();
		address.setIp("1.2.3.4");
		EndpointPort port = new EndpointPort();
		port.setPort(8080);
		KubernetesServiceInstance instance = new KubernetesServiceInstance("123",
				"myservice", address.getIp(), port.getPort(), Collections.emptyMap(),
				secure);

		assertThat(instance.getInstanceId()).isEqualTo("123");
		assertThat(instance.getServiceId()).isEqualTo("myservice");
		assertThat(instance.getHost()).isEqualTo("1.2.3.4");
		assertThat(instance.getPort()).isEqualTo(8080);
		assertThat(instance.isSecure()).isEqualTo(secure);
		assertThat(instance.getScheme()).isEqualTo(secure ? "https" : "http");
		return instance;
	}

	@Test
	public void schemeIsHttps() {
		assertServiceInstance(true);
	}

}
