/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.kubernetes.istio.trace;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * the HeaderPropagationHolder description.
 *
 * @author wuzishu
 */
public final class HeaderPropagationHolder {

	private static final TransmittableThreadLocal<Map<String, String>> headers = new TransmittableThreadLocal<Map<String, String>>() {
		@Override
		protected Map<String, String> initialValue() {
			return new HashMap<>();
		}
	};

	private HeaderPropagationHolder() {
	}

	/**
	 * Sync.
	 * @param all the all
	 */
	public static void sync(Map<String, String> all) {
		headers.set(all);
	}

	/**
	 * Put.
	 * @param header header name.
	 * @param value header value.
	 */
	public static void put(String header, String value) {
		Map<String, String> map = headers.get();
		if (map == null) {
			map = new HashMap<>();
		}
		map.put(header, value);
		headers.set(map);
	}

	/**
	 * Get string.
	 * @param header header name
	 * @return the string
	 */
	public static String get(String header) {
		Map<String, String> map = headers.get();
		if (map == null) {
			map = new HashMap<>();
		}
		return map.get(header);
	}

	/**
	 * Entries set.
	 * @return the set
	 */
	public static Set<Map.Entry<String, String>> entries() {
		Map<String, String> map = headers.get();
		if (map == null) {
			map = new HashMap<>();
		}
		return map.entrySet();
	}

	/**
	 * All map.
	 * @return the map
	 */
	public static Map<String, String> all() {
		Map<String, String> map = headers.get();
		if (map == null) {
			map = new HashMap<>();
		}
		Map<String, String> treeMap = new TreeMap<>(
				Comparator.comparing(String::toLowerCase));
		for (Map.Entry<String, String> entry : map.entrySet()) {
			treeMap.put(entry.getKey(), entry.getValue());
		}
		return Collections.unmodifiableMap(treeMap);
	}

}
