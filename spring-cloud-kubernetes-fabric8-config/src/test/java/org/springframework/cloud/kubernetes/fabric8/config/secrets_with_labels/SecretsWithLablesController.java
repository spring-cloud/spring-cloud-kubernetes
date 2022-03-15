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

package org.springframework.cloud.kubernetes.fabric8.config.secrets_with_labels;

import org.springframework.cloud.kubernetes.fabric8.config.secrets_with_labels.properties.One;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecretsWithLablesController {

	private final org.springframework.cloud.kubernetes.fabric8.config.secrets_with_labels.properties.One one;

	public SecretsWithLablesController(One one) {
		this.one = one;
	}

	@GetMapping("secrets/labels/one")
	public String one() {
		return one.getProperty();
	}

}
