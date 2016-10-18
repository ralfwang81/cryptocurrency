package com.itranswarp.cryptocurrency.common;

import java.math.BigInteger;
import java.security.SecureRandom;

import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

public class KeyPair {

	private final BigInteger privateKey;
	private BigInteger[] publicKey = null;

	private KeyPair(BigInteger privateKey) {
		this.privateKey = privateKey;
	}

	/**
	 * Create KeyPair with specified WIF string.
	 */
	public static KeyPair of(String wif) {
		byte[] key = parseWIF(wif);
		return of(key);
	}

	/**
	 * Create KeyPair with specified private key.
	 */
	public static KeyPair of(byte[] privateKey) {
		return of(new BigInteger(1, privateKey));
	}

	/**
	 * Create KeyPair with specified private key.
	 */
	public static KeyPair of(BigInteger privateKey) {
		checkPrivateKey(privateKey);
		return new KeyPair(privateKey);
	}

	/**
	 * Create a new KeyPair with random private key.
	 */
	public static KeyPair newKeyPair() {
		return of(generatePrivateKey());
	}

	/**
	 * Get private key as BigInteger.
	 */
	public BigInteger getPrivateKey() {
		return this.privateKey;
	}

	/**
	 * Get public key as BigInteger[] with 2 elements.
	 */
	public BigInteger[] getPublicKey() {
		if (this.publicKey == null) {
			ECPoint point = Secp256k1.getG().multiply(privateKey);
			ECPoint normed = point.normalize();
			byte[] x = normed.getXCoord().getEncoded();
			byte[] y = normed.getYCoord().getEncoded();
			this.publicKey = new BigInteger[] { new BigInteger(1, x), new BigInteger(1, y) };
		}
		return this.publicKey;
	}

	/**
	 * Get version 1 of BitCoin address (hash of public key):
	 * https://en.bitcoin.it/wiki/
	 * Technical_background_of_version_1_Bitcoin_addresses
	 */
	public String getAddress() {
		BigInteger[] keys = getPublicKey();
		return publicKeyToAddress(keys[0], keys[1]);
	}

	static String publicKeyToAddress(BigInteger x, BigInteger y) {
		byte[] xs = bigIntegerToBytes(x, 32);
		byte[] ys = bigIntegerToBytes(y, 32);
		byte[] uncompressed = concat(PUBLIC_KEY_PREFIX_ARRAY, xs, ys);
		byte[] hash = Hash.ripeMd160(Hash.sha256(uncompressed));
		return hashToPublicKey(hash);
	}

	public static String hashToPublicKey(byte[] hash) {
		byte[] hashWithNetworkId = concat(NETWORK_ID_ARRAY, hash);
		byte[] checksum = Hash.doubleSha256(hashWithNetworkId);
		byte[] address = concat(hashWithNetworkId, Arrays.copyOfRange(checksum, 0, 4));
		return Base58.encode(address);
	}

	/**
	 * Get Wallet Import Format string defined in:
	 * https://en.bitcoin.it/wiki/Wallet_import_format
	 */
	public String getWalletImportFormat() {
		byte[] key = bigIntegerToBytes(this.privateKey, 32);
		byte[] extendedKey = concat(PRIVATE_KEY_PREFIX_ARRAY, key);
		byte[] hash1 = Hash.sha256(extendedKey);
		byte[] hash2 = Hash.sha256(hash1);
		byte[] checksum = Arrays.copyOfRange(hash2, 0, 4);
		byte[] extendedKeyWithChecksum = concat(extendedKey, checksum);
		return Base58.encode(extendedKeyWithChecksum);
	}

	static byte[] parseWIF(String wif) {
		byte[] data = Base58.decodeChecked(wif);
		if (data[0] != PRIVATE_KEY_PREFIX) {
			throw new IllegalArgumentException("Leading byte is not 0x80.");
		}
		// remove first 0x80:
		return Arrays.copyOfRange(data, 1, data.length);
	}

	static byte[] concat(byte[] buf1, byte[] buf2) {
		byte[] buffer = new byte[buf1.length + buf2.length];
		int offset = 0;
		System.arraycopy(buf1, 0, buffer, offset, buf1.length);
		offset += buf1.length;
		System.arraycopy(buf2, 0, buffer, offset, buf2.length);
		return buffer;
	}

	static byte[] concat(byte[] buf1, byte[] buf2, byte[] buf3) {
		byte[] buffer = new byte[buf1.length + buf2.length + buf3.length];
		int offset = 0;
		System.arraycopy(buf1, 0, buffer, offset, buf1.length);
		offset += buf1.length;
		System.arraycopy(buf2, 0, buffer, offset, buf2.length);
		offset += buf2.length;
		System.arraycopy(buf3, 0, buffer, offset, buf3.length);
		return buffer;
	}

	static byte[] generatePrivateKey() {
		byte[] hash = null;
		int first;
		SecureRandom sr = new SecureRandom();
		do {
			byte[] rnd = new byte[100 + sr.nextInt(100)];
			sr.nextBytes(rnd);
			hash = Hash.sha256(rnd);
			first = hash[0] & 0xff;
		} while (first == 0x00 || first == 0xff);
		System.out.println(Hash.toHexString(hash));
		return hash;
	}

	static byte[] bigIntegerToBytes(BigInteger bi, int length) {
		byte[] data = bi.toByteArray();
		if (data.length == length) {
			return data;
		}
		// remove leading zero:
		if (data[0] == 0) {
			data = Arrays.copyOfRange(data, 1, data.length);
		}
		if (data.length > length) {
			throw new IllegalArgumentException("BigInteger is too large.");
		}
		byte[] copy = new byte[length];
		System.arraycopy(data, 0, copy, length - data.length, data.length);
		return copy;
	}

	static void checkPrivateKey(BigInteger bi) {
		if (bi == null) {
			throw new IllegalArgumentException("Private key is null.");
		}
		if (bi.compareTo(MIN_PRIVATE_KEY) == (-1)) {
			throw new IllegalArgumentException("Private key is too small.");
		}
		if (bi.compareTo(MAX_PRIVATE_KEY) == 1) {
			throw new IllegalArgumentException("Private key is too large.");
		}
	}

	static final byte NETWORK_ID = 0x00;
	static final byte[] NETWORK_ID_ARRAY = { NETWORK_ID };

	static final byte PUBLIC_KEY_PREFIX = 0x04;
	static final byte[] PUBLIC_KEY_PREFIX_ARRAY = { PUBLIC_KEY_PREFIX };

	static final byte PRIVATE_KEY_PREFIX = (byte) 0x80;
	static final byte[] PRIVATE_KEY_PREFIX_ARRAY = { PRIVATE_KEY_PREFIX };

	private static final BigInteger MIN_PRIVATE_KEY = new BigInteger("ffffffffffffffff", 16);
	private static final BigInteger MAX_PRIVATE_KEY = new BigInteger(
			"fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140", 16);

	@Override
	public String toString() {
		return "KeyPair<" + this.getPrivateKey().toString(16) + ">";
	}
}