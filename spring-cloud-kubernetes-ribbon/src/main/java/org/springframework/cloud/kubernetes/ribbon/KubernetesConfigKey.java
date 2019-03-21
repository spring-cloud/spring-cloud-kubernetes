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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import com.netflix.client.config.IClientConfigKey;

import org.springframework.util.Assert;

/**
 * Kubernetes implementation of a Ribbon {@link IClientConfigKey}.
 *
 * @param <T> type of key
 * @author Ioannis Canellos
 */
public abstract class KubernetesConfigKey<T> implements IClientConfigKey<T> {

	/**
	 * Namespace configuration key.
	 */
	public static final IClientConfigKey<String> Namespace = new KubernetesConfigKey<String>(
			"KubernetesNamespace") {
	};

	/**
	 * Port name configuration key.
	 */
	public static final IClientConfigKey<String> PortName = new KubernetesConfigKey<String>(
			"PortName") {
	};

	private static final Set<IClientConfigKey> keys = new HashSet<IClientConfigKey>();

	static {
		for (Field f : KubernetesConfigKey.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) // &&
													// Modifier.isPublic(f.getModifiers())
					&& IClientConfigKey.class.isAssignableFrom(f.getType())) {
				try {
					keys.add((IClientConfigKey) f.get(null));
				}
				catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private final String configKey;

	private final Class<T> type;

	@SuppressWarnings("unchecked")
	protected KubernetesConfigKey(String configKey) {
		this.configKey = configKey;
		Type superclass = getClass().getGenericSuperclass();
		Assert.isTrue(superclass instanceof ParameterizedType,
				superclass + " isn't parameterized");
		Type runtimeType = ((ParameterizedType) superclass).getActualTypeArguments()[0];
		this.type = (Class<T>) Types.rawType(runtimeType);
	}

	/**
	 * @deprecated see {@link #keys()}
	 * @return array of {@link IClientConfigKey}
	 */
	@Deprecated
	public static IClientConfigKey[] values() {
		return keys().toArray(new IClientConfigKey[0]);
	}

	/**
	 * @return all the public static keys defined in this class
	 */
	public static Set<IClientConfigKey> keys() {
		return keys;
	}

	public static IClientConfigKey valueOf(final String name) {
		for (IClientConfigKey key : keys()) {
			if (key.key().equals(name)) {
				return key;
			}
		}
		return new IClientConfigKey() {
			@Override
			public String key() {
				return name;
			}

			@Override
			public Class type() {
				return String.class;
			}
		};
	}

	@Override
	public Class<T> type() {
		return this.type;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.netflix.niws.client.ClientConfig#key()
	 */
	@Override
	public String key() {
		return this.configKey;
	}

	@Override
	public String toString() {
		return this.configKey;
	}

}
