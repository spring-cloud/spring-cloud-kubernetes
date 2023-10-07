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

package org.springframework.cloud.kubernetes.fabric8.reload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author wind57
 */
@ConfigurationProperties("left")
public class LeftProperties {

	private String value;

<<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/fabric8-client-configmap-polling-and-import-reload/src/main/java/org/springframework/cloud/kubernetes/fabric8/configmap/polling/reload/ConfigMapController.java
	private final ConfigMapPropertiesNoMount propertiesNoMount;

	public ConfigMapController(ConfigMapProperties properties, ConfigMapPropertiesNoMount propertiesNoMount) {
		this.properties = properties;
		this.propertiesNoMount = propertiesNoMount;
========
	public String getValue() {
		return value;
>>>>>>>> main:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-fabric8-client-reload/src/main/java/org/springframework/cloud/kubernetes/fabric8/reload/LeftProperties.java
	}

	public void setValue(String value) {
		this.value = value;
	}

	@GetMapping("/key-no-mount")
	public String keyNoMount() {
		return propertiesNoMount.getKey();
	}

}
