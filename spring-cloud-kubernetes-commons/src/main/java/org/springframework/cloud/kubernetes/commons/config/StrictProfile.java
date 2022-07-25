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

package org.springframework.cloud.kubernetes.commons.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/**
 * @author wind57
 *
 * shows whether presence of this profile based source is strictly enforced, meaning we
 * treat it as a fatal error if it is not found.
 */
public final record StrictProfile(String name, boolean strict) {

	/**
	 * returns a mutable LinkedHashSet where all entries are non-strict.
	 */
	static LinkedHashSet<StrictProfile> allNonStrict(String[] profileNames) {
		return Arrays.stream(profileNames).map(name -> new StrictProfile(name, false))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/**
	 * returns a mutable LinkedHashSet where all entries are strict.
	 */
	static LinkedHashSet<StrictProfile> allStrict(LinkedHashSet<String> profileNames) {
		return profileNames.stream().map(name -> new StrictProfile(name, true))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

}
