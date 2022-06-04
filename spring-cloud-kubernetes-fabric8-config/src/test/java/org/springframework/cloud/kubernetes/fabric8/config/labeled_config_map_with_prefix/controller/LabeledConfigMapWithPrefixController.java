/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.controller;

import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.Four;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.One;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.Three;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.Two;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LabeledConfigMapWithPrefixController {

	private final One one;

	private final Two two;

	private final Three three;

	private final Four four;

	public LabeledConfigMapWithPrefixController(One one, Two two, Three three, Four four) {
		this.one = one;
		this.two = two;
		this.three = three;
		this.four = four;
	}

	@GetMapping("/labeled-configmap/prefix/one")
	public String one() {
		return one.getProperty();
	}

	@GetMapping("/labeled-configmap/prefix/two")
	public String two() {
		return two.getProperty();
	}

	@GetMapping("/labeled-configmap/prefix/three")
	public String three() {
		return three.getProperty();
	}

	@GetMapping("/labeled-configmap/prefix/four")
	public String four() {
		return four.getProperty();
	}

}
