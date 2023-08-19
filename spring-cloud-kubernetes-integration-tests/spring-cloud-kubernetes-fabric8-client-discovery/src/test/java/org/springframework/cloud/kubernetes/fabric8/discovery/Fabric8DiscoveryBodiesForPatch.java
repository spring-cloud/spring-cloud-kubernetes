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

package org.springframework.cloud.kubernetes.fabric8.discovery;

/**
 * @author wind57
 */
final class Fabric8DiscoveryBodiesForPatch {

	static final String BODY_ONE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-discovery",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_KUBERNETES_DISCOVERY_INCLUDEEXTERNALNAMESERVICES",
									"value": "TRUE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static final String BODY_TWO = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-discovery",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_REACTIVE_ENABLED",
									"value": "FALSE"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static final String BODY_THREE = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-discovery",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH_REACTIVE",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY_REACTIVE",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								}
								]
							}]
						}
					}
				}
			}
						""";

	static final String BODY_FOUR = """
			{
				"spec": {
					"template": {
						"spec": {
							"containers": [{
								"name": "spring-cloud-kubernetes-fabric8-client-discovery",
								"image": "image_name_here",
								"env": [
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_COMMONS_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_CLIENT_DISCOVERY_HEALTH_REACTIVE",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY_REACTIVE",
									"value": "DEBUG"
								},
								{
									"name": "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_CLOUD_KUBERNETES_FABRIC8_DISCOVERY",
									"value": "DEBUG"
								},
								{
									"name": "SPRING_CLOUD_DISCOVERY_BLOCKING_ENABLED",
									"value": "FALSE"
								}
								]
							}]
						}
					}
				}
			}
						""";


}
