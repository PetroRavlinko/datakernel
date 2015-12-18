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

package io.datakernel.aggregation_db;

import com.google.common.collect.ImmutableMap;
import io.datakernel.aggregation_db.fieldtype.FieldType;
import io.datakernel.aggregation_db.fieldtype.FieldTypeInt;
import io.datakernel.aggregation_db.fieldtype.FieldTypeLong;
import io.datakernel.aggregation_db.keytype.KeyType;
import io.datakernel.aggregation_db.keytype.KeyTypeInt;
import io.datakernel.async.CompletionCallback;
import io.datakernel.async.ResultCallback;
import io.datakernel.codegen.utils.DefiningClassLoader;
import io.datakernel.eventloop.NioEventloop;
import io.datakernel.stream.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.datakernel.aggregation_db.KeyValueTest.KeyValuePair;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AggregationChunkerTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void test() throws IOException {
		final NioEventloop eventloop = new NioEventloop();
		DefiningClassLoader classLoader = new DefiningClassLoader();
		AggregationMetadataStorage aggregationMetadataStorage = new AggregationMetadataStorageStub();
		AggregationMetadata aggregationMetadata = new AggregationMetadata("key-value", KeyValuePair.KEYS, KeyValuePair.FIELDS);
		AggregationStructure aggregationStructure = new AggregationStructure(classLoader,
				ImmutableMap.<String, KeyType>builder()
						.put("key", new KeyTypeInt())
						.build(),
				ImmutableMap.<String, FieldType>builder()
						.put("value", new FieldTypeInt())
						.put("timestamp", new FieldTypeLong())
						.build());
		final List<StreamConsumer> listConsumers = new ArrayList<>();

		final Map<Long, List> map = new HashMap<>();
		AggregationChunkStorage aggregationChunkStorage = new AggregationChunkStorage() {

			@Override
			public <T> StreamProducer<T> chunkReader(String aggregationId, List<String> keys, List<String> fields, Class<T> recordClass, long id) {
				return new StreamProducers.OfIterator<T>(eventloop, map.get(aggregationId).iterator());
			}

			@Override
			public <T> StreamConsumer<T> chunkWriter(String aggregationId, List<String> keys, List<String> fields, Class<T> recordClass, long id, CompletionCallback callback) {
				List<T> list = new ArrayList<>();
				map.put(id, list);
				StreamConsumers.ToList<T> consumer = StreamConsumers.toList(eventloop, list);
				consumer.setCompletionCallback(callback);
				listConsumers.add(consumer);
				return consumer;
			}

			@Override
			public void removeChunk(String aggregationId, long id, CompletionCallback callback) {

			}
		};

		String id = aggregationMetadata.getId();
		List<String> keys = aggregationMetadata.getKeys();

		final List<List<AggregationChunk.NewChunk>> list = new ArrayList<>();
		ResultCallback<List<AggregationChunk.NewChunk>> resultCallback = new ResultCallback<List<AggregationChunk.NewChunk>>() {

			@Override
			public void onException(Exception exception) {
			}

			@Override
			public void onResult(List<AggregationChunk.NewChunk> result) {
				list.add(result);
			}
		};

		final List<AggregationChunk> chunksToConsolidate = aggregationMetadata.findChunksToConsolidate();

		List<String> fields = new ArrayList<>();
		for (AggregationChunk chunk : chunksToConsolidate) {
			for (String field : chunk.getFields()) {
				if (!fields.contains(field)) {
					fields.add(field);
				}
			}
		}

		Class<?> recordClass = aggregationStructure.createRecordClass(keys, fields);

		AggregationChunker aggregationChunker = new AggregationChunker<>(eventloop,
				id, keys, fields, recordClass,
				aggregationChunkStorage, aggregationMetadataStorage, 1, resultCallback);

		StreamProducer<KeyValuePair> producer = StreamProducers.ofIterable(eventloop,
				asList(new KeyValuePair(3, 4, 6), new KeyValuePair(3, 6, 7), new KeyValuePair(1, 2, 1)));
		producer.streamTo(aggregationChunker);

		eventloop.run();

		assertTrue(list.size() == 1);
		assertTrue(list.get(0).size() == 3);

		assertEquals(map.get(1L), asList(new KeyValuePair(3, 4, 6)));
		assertEquals(map.get(2L), asList(new KeyValuePair(3, 6, 7)));
		assertEquals(map.get(3L), asList(new KeyValuePair(1, 2, 1)));

		assertEquals(StreamStatus.END_OF_STREAM, producer.getProducerStatus());
		assertEquals(StreamStatus.END_OF_STREAM, aggregationChunker.getConsumerStatus());
		assertEquals(((StreamProducers.OfIterator) producer).getDownstream(), aggregationChunker);
		for (StreamConsumer consumer : listConsumers) {
			assertEquals(consumer.getConsumerStatus(), StreamStatus.END_OF_STREAM);
		}
	}

	@Test
	public void testProducerWithError() throws IOException {
		final NioEventloop eventloop = new NioEventloop();
		DefiningClassLoader classLoader = new DefiningClassLoader();
		AggregationMetadataStorage aggregationMetadataStorage = new AggregationMetadataStorageStub();
		AggregationMetadata aggregationMetadata = new AggregationMetadata("key-value", KeyValuePair.KEYS, KeyValuePair.FIELDS);
		AggregationStructure aggregationStructure = new AggregationStructure(classLoader,
				ImmutableMap.<String, KeyType>builder()
						.put("key", new KeyTypeInt())
						.build(),
				ImmutableMap.<String, FieldType>builder()
						.put("value", new FieldTypeInt())
						.put("timestamp", new FieldTypeLong())
						.build());
		final List<StreamConsumer> listConsumers = new ArrayList<>();
		AggregationChunkStorage aggregationChunkStorage = new AggregationChunkStorage() {
			Map<String, List> map = new HashMap<>();

			@Override
			public <T> StreamProducer<T> chunkReader(String aggregationId, List<String> keys, List<String> fields, Class<T> recordClass, long id) {
				return new StreamProducers.OfIterator<T>(eventloop, map.get(aggregationId).iterator());
			}

			@Override
			public <T> StreamConsumer<T> chunkWriter(String aggregationId, List<String> keys, List<String> fields, Class<T> recordClass, long id, CompletionCallback callback) {
				List<T> list = new ArrayList<>();
				map.put(aggregationId, list);
				StreamConsumers.ToList<T> consumer = StreamConsumers.toList(eventloop, list);
				consumer.setCompletionCallback(callback);
				listConsumers.add(consumer);
				return consumer;
			}

			@Override
			public void removeChunk(String aggregationId, long id, CompletionCallback callback) {

			}
		};

		String id = aggregationMetadata.getId();
		List<String> keys = aggregationMetadata.getKeys();

		final List<List<AggregationChunk.NewChunk>> list = new ArrayList<>();
		ResultCallback<List<AggregationChunk.NewChunk>> resultCallback = new ResultCallback<List<AggregationChunk.NewChunk>>() {

			@Override
			public void onException(Exception exception) {
			}

			@Override
			public void onResult(List<AggregationChunk.NewChunk> result) {
				list.add(result);
			}
		};

		final List<AggregationChunk> chunksToConsolidate = aggregationMetadata.findChunksToConsolidate();

		List<String> fields = new ArrayList<>();
		for (AggregationChunk chunk : chunksToConsolidate) {
			for (String field : chunk.getFields()) {
				if (!fields.contains(field)) {
					fields.add(field);
				}
			}
		}

		Class<?> recordClass = aggregationStructure.createRecordClass(keys, fields);

		AggregationChunker aggregationChunker = new AggregationChunker<>(eventloop,
				id, keys, fields, recordClass,
				aggregationChunkStorage, aggregationMetadataStorage, 1, resultCallback);

		StreamProducer<KeyValuePair> producer = StreamProducers.concat(eventloop,
				StreamProducers.ofIterable(eventloop, asList(new KeyValuePair(1, 1, 0), new KeyValuePair(1, 2, 1),
						new KeyValuePair(1, 1, 2))),
				StreamProducers.ofIterable(eventloop, asList(new KeyValuePair(1, 1, 0), new KeyValuePair(1, 2, 1),
						new KeyValuePair(1, 1, 2))),
				StreamProducers.ofIterable(eventloop, asList(new KeyValuePair(1, 1, 0), new KeyValuePair(1, 2, 1),
						new KeyValuePair(1, 1, 2))),
				StreamProducers.ofIterable(eventloop, asList(new KeyValuePair(1, 1, 0), new KeyValuePair(1, 2, 1),
						new KeyValuePair(1, 1, 2))),
				StreamProducers.<KeyValuePair>closingWithError(eventloop, new Exception("Test Exception"))
		);

		producer.streamTo(aggregationChunker);

		eventloop.run();

		assertTrue(list.size() == 0);
		assertEquals(StreamStatus.CLOSED_WITH_ERROR, producer.getProducerStatus());
		assertEquals(StreamStatus.CLOSED_WITH_ERROR, aggregationChunker.getConsumerStatus());
		for (int i = 0; i < listConsumers.size() - 1; i++) {
			assertEquals(listConsumers.get(i).getConsumerStatus(), StreamStatus.END_OF_STREAM);
		}
		assertEquals(getLast(listConsumers).getConsumerStatus(), StreamStatus.CLOSED_WITH_ERROR);
	}

	static <T> T getLast(List<T> list) {
		return list.get(list.size() - 1);
	}

	@Test
	public void testStorageConsumerWithError() throws IOException {
		final NioEventloop eventloop = new NioEventloop();
		DefiningClassLoader classLoader = new DefiningClassLoader();
		AggregationMetadataStorage aggregationMetadataStorage = new AggregationMetadataStorageStub();
		AggregationMetadata aggregationMetadata = new AggregationMetadata("key-value", KeyValuePair.KEYS, KeyValuePair.FIELDS);
		AggregationStructure aggregationStructure = new AggregationStructure(classLoader,
				ImmutableMap.<String, KeyType>builder()
						.put("key", new KeyTypeInt())
						.build(),
				ImmutableMap.<String, FieldType>builder()
						.put("value", new FieldTypeInt())
						.put("timestamp", new FieldTypeLong())
						.build());
		final List<StreamConsumer> listConsumers = new ArrayList<>();
		AggregationChunkStorage aggregationChunkStorage = new AggregationChunkStorage() {
			Map<String, List> map = new HashMap<>();

			@Override
			public <T> StreamProducer<T> chunkReader(String aggregationId, List<String> keys, List<String> fields, Class<T> recordClass, long id) {
				return new StreamProducers.OfIterator<T>(eventloop, map.get(aggregationId).iterator());
			}

			@Override
			public <T> StreamConsumer<T> chunkWriter(String aggregationId, List<String> keys, List<String> fields, Class<T> recordClass, long id, CompletionCallback callback) {
				List<T> list = new ArrayList<>();
				map.put(aggregationId, list);
				if (id == 1) {
					StreamConsumers.ToList<T> toList = StreamConsumers.toList(eventloop, list);
					listConsumers.add(toList);
					return toList;
				} else {
					StreamConsumers.ClosingWithError<T> consumer = StreamConsumers.closingWithError(eventloop, new Exception("Test Exception"));
					consumer.setCompletionCallback(callback);
					listConsumers.add(consumer);
					return consumer;
				}
			}

			@Override
			public void removeChunk(String aggregationId, long id, CompletionCallback callback) {

			}
		};

		String id = aggregationMetadata.getId();
		List<String> keys = aggregationMetadata.getKeys();

		final List<List<AggregationChunk.NewChunk>> list = new ArrayList<>();
		ResultCallback<List<AggregationChunk.NewChunk>> resultCallback = new ResultCallback<List<AggregationChunk.NewChunk>>() {

			@Override
			public void onException(Exception exception) {
			}

			@Override
			public void onResult(List<AggregationChunk.NewChunk> result) {
				list.add(result);
			}
		};

		final List<AggregationChunk> chunksToConsolidate = aggregationMetadata.findChunksToConsolidate();

		List<String> fields = new ArrayList<>();
		for (AggregationChunk chunk : chunksToConsolidate) {
			for (String field : chunk.getFields()) {
				if (!fields.contains(field)) {
					fields.add(field);
				}
			}
		}

		Class<?> recordClass = aggregationStructure.createRecordClass(keys, fields);

		AggregationChunker aggregationChunker = new AggregationChunker<>(eventloop,
				id, keys, fields, recordClass,
				aggregationChunkStorage, aggregationMetadataStorage, 1, resultCallback);

		StreamProducer<KeyValuePair> producer = StreamProducers.ofIterable(eventloop, asList(new KeyValuePair(1, 1, 0), new KeyValuePair(1, 2, 1),
				new KeyValuePair(1, 1, 2), new KeyValuePair(1, 1, 2), new KeyValuePair(1, 1, 2))
		);
		producer.streamTo(aggregationChunker);

		eventloop.run();

		assertTrue(list.size() == 0);
		assertEquals(StreamStatus.CLOSED_WITH_ERROR, producer.getProducerStatus());
		assertEquals(((StreamProducers.OfIterator) producer).getDownstream(), aggregationChunker);
		assertEquals(StreamStatus.CLOSED_WITH_ERROR, aggregationChunker.getConsumerStatus());
		for (int i = 0; i < listConsumers.size() - 1; i++) {
			assertEquals(listConsumers.get(i).getConsumerStatus(), StreamStatus.END_OF_STREAM);
		}
		assertEquals(getLast(listConsumers).getConsumerStatus(), StreamStatus.CLOSED_WITH_ERROR);
	}
}
