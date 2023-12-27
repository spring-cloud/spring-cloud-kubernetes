/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.kubernetes.client.config.sanitize_secrets;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
class SanitizeController {

	private final SanitizeProperties sanitizeProperties;

	SanitizeController(SanitizeProperties sanitizeProperties) {
		this.sanitizeProperties = sanitizeProperties;
	}

	@GetMapping("/secret")
	String secret() {
		return sanitizeProperties.getSanitizeSecretName();
	}

	@GetMapping("/configmap")
	String configmap() {
		return sanitizeProperties.getSanitizeConfigMapName();
	}

}
