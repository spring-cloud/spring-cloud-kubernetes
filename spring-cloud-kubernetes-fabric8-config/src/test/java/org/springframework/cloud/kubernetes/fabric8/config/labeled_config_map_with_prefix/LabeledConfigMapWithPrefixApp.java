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

package org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.Four;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.One;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.Three;
import org.springframework.cloud.kubernetes.fabric8.config.labeled_config_map_with_prefix.properties.Two;

@SpringBootApplication
@EnableConfigurationProperties({ One.class, Two.class, Three.class, Four.class })
public class LabeledConfigMapWithPrefixApp {

	public static void main(String[] args) {
		SpringApplication.run(LabeledConfigMapWithPrefixApp.class, args);
	}

}
