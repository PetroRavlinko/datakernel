/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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
package io.datakernel.examples;

import io.datakernel.serializer.annotations.Deserialize;
import io.datakernel.serializer.annotations.Serialize;
import io.datakernel.serializer.annotations.SerializeNullable;

// [START EXAMPLE]
public class GetResponse {

	private final String value;

	public GetResponse(@Deserialize("value") String value) {
		this.value = value;
	}

	@Serialize(order = 0)
	@SerializeNullable
	public String getValue() {
		return value;
	}
}
// [END EXAMPLE]
