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

package io.datakernel.serializer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Provides methods for reading primitives
 * and Strings from byte arrays
 */
public final class BinaryInput {
	public final byte[] array;
	public int pos;

	public BinaryInput(byte[] array) {
		this.array = array;
	}

	public BinaryInput(byte[] array, int pos) {
		this.array = array;
		this.pos = pos;
	}

	public byte[] array() {
		return array;
	}

	public int pos() {
		return pos;
	}

	public void pos(int pos) {
		this.pos = pos;
	}

	public void move(int delta) {
		this.pos += delta;
	}

	public int read(byte[] b) {
		return read(b, 0, b.length);
	}

	public int read(byte[] b, int off, int len) {
		System.arraycopy(this.array, pos, b, off, len);
		pos += len;
		return len;
	}

	public byte readByte() {
		return array[pos++];
	}

	public boolean readBoolean() {
		return readByte() != 0;
	}

	public short readShort() {
		short result = (short) ((array[pos] & 0xFF) << 8 | array[pos + 1] & 0xFF);
		pos += 2;
		return result;
	}

	public short readShortLE() {
		short result = (short) (array[pos] & 0xFF | (array[pos + 1] & 0xFF) << 8);
		pos += 2;
		return result;
	}

	public char readChar() {
		char c = (char) ((array[pos] & 0xFF) << 8 | array[pos + 1] & 0xFF);
		pos += 2;
		return c;
	}

	public char readCharLE() {
		char c = (char) (array[pos] & 0xFF | (array[pos + 1] & 0xFF) << 8);
		pos += 2;
		return c;
	}

	public int readInt() {
		//noinspection PointlessBitwiseExpression
		int result = 0 |
				(0 |
						(array[pos] & 0xFF) << 24 |
						(array[pos + 1] & 0xFF) << 16
				) |
				(0 |
						(array[pos + 2] & 0xFF) << 8 |
						(array[pos + 3] & 0xFF)
				);
		pos += 4;
		return result;
	}

	public int readIntLE() {
		//noinspection PointlessBitwiseExpression
		int result = 0 |
				(0 |
						(array[pos] & 0xFF) |
						(array[pos + 1] & 0xFF) << 8
				) |
				(0 |
						(array[pos + 2] & 0xFF) << 16 |
						(array[pos + 3] & 0xFF) << 24
				);
		pos += 4;
		return result;
	}

	public long readLong() {
		//noinspection PointlessBitwiseExpression
		long result = 0 |
				(0 |
						(0 |
								(long) (array[pos] & 0xFF) << 56 |
								(long) (array[pos + 1] & 0xFF) << 48
						) |
						(0 |
								(long) (array[pos + 2] & 0xFF) << 40 |
								(long) (array[pos + 3] & 0xFF) << 32
						)
				) |
				(0 |
						(0 |
								(long) (array[pos + 4] & 0xFF) << 24 |
								(array[pos + 5] & 0xFF) << 16
						) |
						(0 |
								(array[pos + 6] & 0xFF) << 8 |
								(array[pos + 7] & 0xFF)
						)
				);
		pos += 8;
		return result;
	}

	public long readLongLE() {
		//noinspection PointlessBitwiseExpression
		long result = 0 |
				(0 |
						(0 |
								(array[pos] & 0xFF) |
								(array[pos + 1] & 0xFF) << 8
						) |
						(0 |
								(array[pos + 2] & 0xFF) << 16 |
								(long) (array[pos + 3] & 0xFF) << 24
						)
				) |
				(0 |
						(0 |
								(long) (array[pos + 4] & 0xFF) << 32 |
								(long) (array[pos + 5] & 0xFF) << 40
						) |
						(0 |
								(long) (array[pos + 6] & 0xFF) << 48 |
								(long) (array[pos + 7] & 0xFF) << 56
						)
				);
		pos += 8;
		return result;
	}

	public int readVarInt() {
		int b;
		int result;
		if ((b = array[pos]) >= 0) {
			pos += 1;
			return b;
		}
		result = b & 0x7f;
		if ((b = array[pos + 1]) >= 0) {
			result |= b << 7;
			pos += 2;
			return result;
		}
		result |= (b & 0x7f) << 7;
		if ((b = array[pos + 2]) >= 0) {
			result |= b << 14;
			pos += 3;
			return result;
		}
		result |= (b & 0x7f) << 14;
		if ((b = array[pos + 3]) >= 0) {
			result |= b << 21;
			pos += 4;
			return result;
		}
		result |= (b & 0x7f) << 21;
		b = array[pos + 4];
		result |= b << 28;
		pos += 5;
		return result;
	}

	public long readVarLong() {
		long b;
		long result;
		b = array[pos++];
		if (b >= 0) {
			return b;
		}
		result = b & 0x7F;
		for (int offset = 7; offset < 64; offset += 7) {
			b = array[pos++];
			if (b >= 0)
				return result | b << offset;
			result |= (b & 0x7F) << offset;
		}
		throw new IllegalStateException("Read varlong was too long");
	}

	public float readFloat() {
		return Float.intBitsToFloat(readInt());
	}

	public double readDouble() {
		return Double.longBitsToDouble(readLong());
	}

	@NotNull
	public String readUTF8() {
		int length = readVarInt();
		if (length == 0) return "";
		pos += length;
		return new String(array, pos - length, length, UTF_8);
	}

	@Nullable
	public String readUTF8Nullable() {
		int length = readVarInt();
		if (length == 0) return null;
		length--;
		if (length == 0) return "";
		pos += length;
		return new String(array, pos - length, length, UTF_8);
	}

	@NotNull
	public String readIso88591() {
		int length = readVarInt();
		if (length == 0) return "";
		if (length >= 40) return readIso88591buf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			int c = array[pos + i] & 0xff;
			chars[i] = (char) c;
		}
		pos += length;
		return new String(chars, 0, length);
	}

	@Nullable
	public String readIso88591Nullable() {
		int length = readVarInt();
		if (length == 0) return null;
		length--;
		if (length == 0) return "";
		if (length >= 40) return readIso88591buf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) (array[pos + i] & 0xff);
		}
		pos += length;
		return new String(chars, 0, length);
	}

	@NotNull
	public String readUTF8mb3() {
		int length = readVarInt();
		if (length == 0) return "";
		if (length >= 40) return readUTF8mb3buf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			byte b = array[pos++];
			chars[i] = b >= 0 ?
					(char) b :
					readUTF8mb3Char(b);
		}
		return new String(chars, 0, length);
	}

	@Nullable
	public String readUTF8mb3Nullable() {
		int length = readVarInt();
		if (length == 0) return null;
		length--;
		if (length == 0) return "";
		if (length >= 40) return readUTF8mb3buf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			byte b = array[pos++];
			chars[i] = b >= 0 ?
					(char) b :
					readUTF8mb3Char(b);
		}
		return new String(chars, 0, length);
	}

	private char readUTF8mb3Char(byte b) {
		int c = b & 0xFF;
		if (c < 0xE0) {
			return (char) ((c & 0x1F) << 6 | array[pos++] & 0x3F);
		} else {
			return (char) ((c & 0x0F) << 12 | (array[pos++] & 0x3F) << 6 | (array[pos++] & 0x3F));
		}
	}

	@NotNull
	public String readUTF16() {
		int length = readVarInt();
		if (length == 0) return "";
		if (length >= 40) return readUTF16buf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) ((array[pos + i * 2] & 0xFF) << 8 | array[pos + i * 2 + 1] & 0xFF);
		}
		pos += length * 2;
		return new String(chars, 0, length);
	}

	@NotNull
	public String readUTF16LE() {
		int length = readVarInt();
		if (length == 0) return "";
		if (length >= 40) return readUTF16LEbuf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) (array[pos + i * 2] & 0xFF | (array[pos + i * 2 + 1] & 0xFF) << 8);
		}
		pos += length * 2;
		return new String(chars, 0, length);
	}

	@Nullable
	public String readUTF16Nullable() {
		int length = readVarInt();
		if (length == 0) return null;
		length--;
		if (length == 0) return "";
		if (length >= 40) return readUTF16buf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) ((array[pos + i * 2] & 0xFF) << 8 | array[pos + i * 2 + 1] & 0xFF);
		}
		pos += length * 2;
		return new String(chars, 0, length);
	}

	@Nullable
	public String readUTF16NullableLE() {
		int length = readVarInt();
		if (length == 0) return null;
		length--;
		if (length == 0) return "";
		if (length >= 40) return readUTF16LEbuf(length);
		char[] chars = new char[length];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) (array[pos + i * 2] & 0xFF | (array[pos + i * 2 + 1] & 0xFF) << 8);
		}
		pos += length * 2;
		return new String(chars, 0, length);
	}

	private static final AtomicReference<char[]> BUF = new AtomicReference<>(new char[256]);

	@NotNull
	private String readIso88591buf(int length) {
		char[] chars = BUF.getAndSet(null);
		if (chars == null || chars.length < length) chars = new char[length + length / 4];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) (array[pos + i] & 0xff);
		}
		pos += length;
		String s = new String(chars, 0, length);
		BUF.lazySet(chars);
		return s;
	}

	@NotNull
	private String readUTF8mb3buf(int length) {
		char[] chars = BUF.getAndSet(null);
		if (chars == null || chars.length < length) chars = new char[length + length / 4];
		for (int i = 0; i < length; i++) {
			byte b = array[pos++];
			chars[i] = b >= 0 ?
					(char) b :
					readUTF8mb3Char(b);
		}
		String s = new String(chars, 0, length);
		BUF.lazySet(chars);
		return s;
	}

	@NotNull
	private String readUTF16buf(int length) {
		char[] chars = BUF.getAndSet(null);
		if (chars == null || chars.length < length) chars = new char[length + length / 4];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) ((array[pos + i * 2] & 0xFF) << 8 | array[pos + i * 2 + 1] & 0xFF);
		}
		pos += length * 2;
		String s = new String(chars, 0, length);
		BUF.lazySet(chars);
		return s;
	}

	@NotNull
	private String readUTF16LEbuf(int length) {
		char[] chars = BUF.getAndSet(null);
		if (chars == null || chars.length < length) chars = new char[length + length / 4];
		for (int i = 0; i < length; i++) {
			chars[i] = (char) (array[pos + i * 2] & 0xFF | (array[pos + i * 2 + 1] & 0xFF) << 8);
		}
		pos += length * 2;
		String s = new String(chars, 0, length);
		BUF.lazySet(chars);
		return s;
	}

}
