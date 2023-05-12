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

package org.springframework.cloud.kubernetes.fabric8.config.sources_order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wind57
 */
@RestController
class SourcesOrderController {

	private final Properties properties;

	SourcesOrderController(Properties properties) {
		this.properties = properties;
	}

	@GetMapping("/key")
	String key() {
		return properties.getKey();
	}

	@GetMapping("/one")
	String one() {
		return properties.getOne();
	}

	@GetMapping("/two")
	String two() {
		return properties.getTwo();
	}

}
