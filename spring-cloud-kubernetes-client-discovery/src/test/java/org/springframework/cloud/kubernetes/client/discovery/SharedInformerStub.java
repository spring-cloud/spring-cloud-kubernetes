/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.kubernetes.client.discovery;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.TransformFunc;
import io.kubernetes.client.informer.cache.Indexer;

/**
 * @author wind57
 */
final class SharedInformerStub<T extends KubernetesObject> implements SharedIndexInformer<T> {

	@Override
	public void addEventHandler(ResourceEventHandler<T> handler) {

	}

	@Override
	public void addEventHandlerWithResyncPeriod(ResourceEventHandler<T> handler, long resyncPeriod) {

	}

	@Override
	public void run() {

	}

	@Override
	public void stop() {

	}

	// this is the only method we care about
	@Override
	public boolean hasSynced() {
		return true;
	}

	@Override
	public String lastSyncResourceVersion() {
		return null;
	}

	@Override
	public void setTransform(TransformFunc transformFunc) {

	}

	@Override
	public void addIndexers(Map<String, Function<T, List<String>>> map) {

	}

	@Override
	public Indexer<T> getIndexer() {
		return null;
	}

}
