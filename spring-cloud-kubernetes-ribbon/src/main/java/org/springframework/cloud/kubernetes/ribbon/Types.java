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

package org.springframework.cloud.kubernetes.ribbon;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

final class Types {

	private Types() {
		// Utlity
	}

	static Class rawType(Type type) {
		if (type instanceof Class) {
			return (Class) type;
		}
		else if (type instanceof TypeVariable) {
			return rawType(firstOrObject(((TypeVariable) type).getBounds()));
		}
		else if (type instanceof WildcardType) {
			return rawType(firstOrObject(((WildcardType) type).getUpperBounds()));
		}
		else if (type instanceof GenericArrayType) {
			return rawType(((GenericArrayType) type).getGenericComponentType());
		}
		return Object.class;
	}

	private static Type firstOrObject(Type[] types) {
		if (types.length > 0) {
			return rawType(types[0]);
		}
		else {
			return Void.class;
		}
	}

}
