/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.kubernetes.commons.discovery;

import java.util.Iterator;
import java.util.List;

/**
 * @author wind57
 */
public record SelectiveNamespaces(List<String> selectiveNamespaces) implements Iterable<String> {

	public int size() {
		return selectiveNamespaces.size();
	}

	public String get(int i) {
		return selectiveNamespaces.get(i);
	}

	@Override
	public Iterator<String> iterator() {
		return selectiveNamespaces.iterator();
	}
}
