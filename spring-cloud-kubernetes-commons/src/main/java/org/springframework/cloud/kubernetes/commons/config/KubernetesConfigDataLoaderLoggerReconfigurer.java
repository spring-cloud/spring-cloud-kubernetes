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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

import org.apache.commons.logging.Log;

import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 *
 * See :
 * <a href="https://github.com/spring-cloud/spring-cloud-kubernetes/issues/1173">this
 * issue</a>.
 *
 * @author wind57
 */
final class KubernetesConfigDataLoaderLoggerReconfigurer {

	private static Log log;

	private KubernetesConfigDataLoaderLoggerReconfigurer() {
	}

	static void reconfigureLoggers(DeferredLogFactory logFactory) {

		log = logFactory.getLog(KubernetesConfigDataLoaderLoggerReconfigurer.class);

		List<Optional<Class<?>>> loggers = List.of(Optional.of(ConfigMapPropertySourceLocator.class),
				Optional.of(SecretsPropertySourceLocator.class), Optional.of(SourceDataEntriesProcessor.class),
				Optional.of(ConfigUtils.class),
				forName("org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator"),
				forName("org.springframework.cloud.kubernetes.client.config.KubernetesClientSecretsPropertySourceLocator"),
				forName("org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigUtils"),
				forName("org.springframework.cloud.kubernetes.fabric8.config.Fabric8ConfigUtils"));

		loggers.forEach(logger -> {
			logger.ifPresent(loggerClass -> {
				log.debug("reconfiguring logger for " + loggerClass);
				reconfigureLogger(loggerClass, logFactory);
			});
		});
	}

	private static Optional<Class<?>> forName(String name) {
		try {
			return Optional.of(ClassUtils.forName(name, KubernetesConfigDataLoader.class.getClassLoader()));
		}
		catch (ClassNotFoundException e) {
			return Optional.empty();
		}
	}

	static void reconfigureLogger(Class<?> type, DeferredLogFactory logFactory) {

		ReflectionUtils.doWithFields(type, field -> {

			field.setAccessible(true);
			field.set(null, logFactory.getLog(type));

		}, KubernetesConfigDataLoaderLoggerReconfigurer::isUpdatableLogField);
	}

	private static boolean isUpdatableLogField(Field field) {
		return !Modifier.isFinal(field.getModifiers()) && field.getType().isAssignableFrom(Log.class);
	}

}
