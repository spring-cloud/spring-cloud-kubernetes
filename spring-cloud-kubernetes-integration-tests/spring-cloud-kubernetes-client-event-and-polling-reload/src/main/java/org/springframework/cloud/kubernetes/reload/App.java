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

package org.springframework.cloud.kubernetes.reload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author wind57
 */
@SpringBootApplication
<<<<<<<< HEAD:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-client-event-and-polling-reload/src/main/java/org/springframework/cloud/kubernetes/reload/ConfigMapApp.java
@EnableConfigurationProperties({ ConfigMapProperties.class, LeftProperties.class, RightProperties.class,
		RightWithLabelsProperties.class })
public class ConfigMapApp {
========
@EnableConfigurationProperties({ LeftProperties.class, RightProperties.class, RightWithLabelsProperties.class,
		ConfigMapProperties.class, SecretsProperties.class })
public class App {
>>>>>>>> main:spring-cloud-kubernetes-integration-tests/spring-cloud-kubernetes-client-event-and-polling-reload/src/main/java/org/springframework/cloud/kubernetes/reload/App.java

	public static void main(String[] args) {
		SpringApplication.run(ConfigMapApp.class, args);
	}

}
