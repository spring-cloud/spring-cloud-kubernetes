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

package org.springframework.cloud.kubernetes.fabric8.client.reload;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
class Controller {

	private final LeftProperties leftProperties;

	private final RightProperties rightProperties;

	private final RightWithLabelsProperties rightWithLabelsProperties;

	private final ConfigMapProperties configMapProperties;

	private final SecretProperties secretProperties;

	Controller(LeftProperties leftProperties, RightProperties rightProperties,
			RightWithLabelsProperties rightWithLabelsProperties, ConfigMapProperties configMapProperties,
			SecretProperties secretProperties) {
		this.leftProperties = leftProperties;
		this.rightProperties = rightProperties;
		this.rightWithLabelsProperties = rightWithLabelsProperties;
		this.configMapProperties = configMapProperties;
		this.secretProperties = secretProperties;
	}

	@GetMapping("/left")
	String left() {
		return leftProperties.getValue();
	}

	@GetMapping("/right")
	String right() {
		return rightProperties.getValue();
	}

	@GetMapping("/with-label")
	String witLabel() {
		return rightWithLabelsProperties.getValue();
	}

	@GetMapping("/key")
	String key() {
		return configMapProperties.getKey();
	}

	@GetMapping("/key-from-secret")
	String keyFromSecret() {
		return secretProperties.getKey();
	}

}
