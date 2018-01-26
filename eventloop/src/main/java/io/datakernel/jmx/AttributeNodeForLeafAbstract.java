/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.jmx;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.datakernel.jmx.Utils.createDescriptionMap;
import static io.datakernel.util.CollectionUtils.first;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

abstract class AttributeNodeForLeafAbstract implements AttributeNode {
	protected final String name;
	private final String description;
	protected final ValueFetcher fetcher;
	private boolean visible;

	public AttributeNodeForLeafAbstract(String name, String description, ValueFetcher fetcher, boolean visible) {
		this.name = name;
		this.description = description;
		this.fetcher = fetcher;
		this.visible = visible;
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public final Set<String> getAllAttributes() {
		return singleton(name);
	}

	@Override
	public final Set<String> getVisibleAttributes() {
		return visible ? singleton(name) : Collections.<String>emptySet();
	}

	@Override
	public final Map<String, Map<String, String>> getDescriptions() {
		return createDescriptionMap(name, description);
	}

	@Override
	public final Map<String, Object> aggregateAttributes(Set<String> attrNames, List<?> sources) {
		assert attrNames.size() == 1;
		String attrName = first(attrNames);
		assert name.equals(attrName);
		assert sources != null;

		if (sources.size() == 0) {
			return singletonMap(attrName, null);
		}

		return Collections.singletonMap(name, aggregateAttribute(attrName, sources));
	}

	// It's guaranteed that list of sources is not empty and it doesn't contain null values
	protected abstract Object aggregateAttribute(String attrName, List<?> sources);

	@Override
	public final boolean isVisible() {
		return visible;
	}

	@Override
	public final void setVisible(String attrName) {
		assert name.equals(attrName);
		this.visible = true;
	}

	@Override
	public final void hideNullPojos(List<?> sources) {}

	@Override
	public final void applyModifier(String attrName, AttributeModifier<?> modifier, List<?> target) {
		assert name.equals(attrName);
		throw new UnsupportedOperationException(String.format(
				"AttributeModifier can be applied only to POJO. Attribute \"%s\" is not a POJO.", attrName
		));
	}
}
