package io.global.common;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.bytebuf.ByteBufPool;
import io.global.globalsync.util.SerializationUtils;

import java.io.IOException;
import java.math.BigInteger;

import static io.global.globalsync.util.SerializationUtils.*;

public final class SignedData<T extends Signable> {
	private final T data;
	private final ECDSASignature signature;

	private SignedData(T data, ECDSASignature signature) {
		this.data = data;
		this.signature = signature;
	}

	public static <T extends Signable> SignedData<T> ofBytes(byte[] bytes, Signable.Parser<T> dataParser) throws IOException {
		ByteBuf buf = ByteBuf.wrapForReading(bytes);
		byte[] dataBytes = SerializationUtils.readBytes(buf);
		T data = dataParser.parseBytes(dataBytes);
		BigInteger r = readBigInteger(buf);
		BigInteger s = readBigInteger(buf);
		return new SignedData<>(data, new ECDSASignature(r, s));
	}

	public static <T extends Signable> SignedData<T> sign(T data, PrivKey privKey) {
		return sign(data, privKey.getPrivateKeyForSigning());
	}

	public static <T extends Signable> SignedData<T> sign(T data, BigInteger privateKeyForSigning) {
		byte[] dataBytes = data.toBytes();
		ECDSASignature signature = CryptoUtils.sign(dataBytes, privateKeyForSigning);
		return new SignedData<>(data, signature);
	}

	public byte[] toBytes() {
		byte[] dataBytes = data.toBytes();
		ByteBuf buf = ByteBufPool.allocate(sizeof(dataBytes) + sizeof(signature.r) + sizeof(signature.s));
		writeBytes(buf, dataBytes);
		writeBigInteger(buf, signature.r);
		writeBigInteger(buf, signature.s);
		return buf.asArray();
	}

	public T getData() {
		return data;
	}

	public ECDSASignature getSignature() {
		return signature;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SignedData<?> that = (SignedData<?>) o;
		if (!data.equals(that.data)) return false;
		return signature.equals(that.signature);
	}

	@Override
	public int hashCode() {
		int result = data.hashCode();
		result = 31 * result + signature.hashCode();
		return result;
	}
}
