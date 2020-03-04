/*
 * Copyright 2012-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.EndpointSubset;

/**
 * @author Haytham Mohamed
 **/
public class EndpointSubsetNS {

	private String namespace;

	private List<EndpointSubset> endpointSubset;

	public EndpointSubsetNS() {
		endpointSubset = new ArrayList<>();
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public List<EndpointSubset> getEndpointSubset() {
		return endpointSubset;
	}

	public void setEndpointSubset(List<EndpointSubset> endpointSubset) {
		this.endpointSubset = endpointSubset;
	}

	public boolean equals(Object o) {
		return this.endpointSubset.equals(o);
	}

	public int hashCode() {
		return this.endpointSubset.hashCode();
	}

}
