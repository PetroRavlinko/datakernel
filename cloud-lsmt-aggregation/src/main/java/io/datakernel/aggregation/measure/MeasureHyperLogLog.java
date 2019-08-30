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

package io.datakernel.aggregation.measure;

import io.datakernel.aggregation.fieldtype.FieldType;
import io.datakernel.codegen.Context;
import io.datakernel.codegen.Expression;
import io.datakernel.codegen.Variable;
import io.datakernel.serializer.asm.SerializerGen;
import io.datakernel.serializer.asm.SerializerGenArray;
import io.datakernel.serializer.asm.SerializerGenByte;
import io.datakernel.serializer.asm.SerializerGenClass;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import static io.datakernel.codec.StructuredCodecs.INT_CODEC;
import static io.datakernel.codegen.Expressions.*;
import static io.datakernel.codegen.Utils.*;
import static java.util.Collections.singletonList;

public final class MeasureHyperLogLog extends Measure {
	private final int registers;

	private static final class FieldTypeHyperLogLog extends FieldType<Integer> {
		public FieldTypeHyperLogLog() {
			super(HyperLogLog.class, int.class, serializerGen(), INT_CODEC, null);
		}

		private static SerializerGen serializerGen() {
			SerializerGenClass serializerGenClass = new SerializerGenClass(HyperLogLog.class);
			try {
				serializerGenClass.addGetter(HyperLogLog.class.getMethod("getRegisters"),
						new SerializerGenArray(new SerializerGenByte(), byte[].class), -1, -1);
				serializerGenClass.setConstructor(HyperLogLog.class.getConstructor(byte[].class),
						singletonList("registers"));
			} catch (NoSuchMethodException ignored) {
				throw new RuntimeException("Unable to construct SerializerGen for HyperLogLog");
			}
			return serializerGenClass;
		}
	}

	MeasureHyperLogLog(int registers) {
		super(new FieldTypeHyperLogLog());
		this.registers = registers;
	}

	public static MeasureHyperLogLog create(int registers) {
		return new MeasureHyperLogLog(registers);
	}

	@Override
	public Expression valueOfAccumulator(Expression accumulator) {
		return call(accumulator, "estimate");
	}

	@Override
	public Expression zeroAccumulator(Variable accumulator) {
		return set(accumulator, constructor(HyperLogLog.class, value(registers)));
	}

	@Override
	public Expression initAccumulatorWithAccumulator(Variable accumulator, Expression firstAccumulator) {
		return sequence(
				set(accumulator, constructor(HyperLogLog.class, value(registers))),
				call(accumulator, "union", firstAccumulator));
	}

	@Override
	public Expression reduce(Variable accumulator,
			Variable nextAccumulator) {
		return call(accumulator, "union", nextAccumulator);
	}

	@Override
	public Expression initAccumulatorWithValue(Variable accumulator,
			Variable firstValue) {
		return sequence(
				set(accumulator, constructor(HyperLogLog.class, value(registers))),
				add(accumulator, firstValue));
	}

	@Override
	public Expression accumulate(Variable accumulator, Variable nextValue) {
		return add(accumulator, nextValue);
	}

	private static Expression add(Expression accumulator, Expression value) {
		return new ExpressionHyperLogLog(value, accumulator);
	}

	private static class ExpressionHyperLogLog implements Expression {
		private final Expression value;
		private final Expression accumulator;

		public ExpressionHyperLogLog(Expression value, Expression accumulator) {
			this.value = value;
			this.accumulator = accumulator;
		}

		@Override
		public Type load(Context ctx) {
			GeneratorAdapter g = ctx.getGeneratorAdapter();
			Type accumulatorType = accumulator.load(ctx);
			Type valueType = value.load(ctx);
			String methodName;
			Type methodParameterType;
			if (valueType == Type.LONG_TYPE || valueType.getClassName().equals(Long.class.getName())) {
				methodName = "addLong";
				methodParameterType = Type.LONG_TYPE;
			} else if (valueType == Type.INT_TYPE || valueType.getClassName().equals(Integer.class.getName())) {
				methodName = "addInt";
				methodParameterType = Type.INT_TYPE;
			} else {
				methodName = "addObject";
				methodParameterType = Type.getType(Object.class);
			}

			if (isWrapperType(valueType)) {
				g.unbox(methodParameterType);
			}

			invokeVirtualOrInterface(g, getJavaType(ctx.getClassLoader(), accumulatorType),
					new org.objectweb.asm.commons.Method(methodName, Type.VOID_TYPE, new Type[]{methodParameterType}));

			return Type.VOID_TYPE;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ExpressionHyperLogLog that = (ExpressionHyperLogLog) o;
			return value.equals(that.value) &&
					accumulator.equals(that.accumulator);
		}

		@Override
		public int hashCode() {
			int result = value.hashCode();
			result = 31 * result + accumulator.hashCode();
			return result;
		}
	}
}
