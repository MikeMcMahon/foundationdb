/*
 * ReadTransaction.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2013-2018 Apple Inc. and the FoundationDB project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apple.foundationdb;

import java.util.concurrent.CompletableFuture;

import com.apple.foundationdb.async.AsyncIterable;
import com.apple.foundationdb.async.AsyncIterator;
import com.apple.foundationdb.tuple.Tuple;

/**
 * A read-only subset of a FoundationDB {@link Transaction}. This is the interface that
 *  {@code Transaction}'s {@link Transaction#snapshot snapshot} presents.<br>
 * <br>
 * <b>Note:</b> Client must call {@link Transaction#commit()} and wait on the result on all transactions,
 *  even ones that only read. This is done automatically when using the retry loops from
 *  {@link Database#run(java.util.function.Function)}. This is explained more in the intro to {@link Transaction}.
 *
 * @see Transaction
 */
public interface ReadTransaction extends ReadTransactionContext {
	/**
	 * When passed to a {@code getRange()} call that takes a {@code limit} parameter,
	 *  indicates that the query should return unlimited rows.
	 */
	int ROW_LIMIT_UNLIMITED = 0;

	/**
	 * Gets whether this transaction is a snapshot view of the database. In other words, this returns
	 *  whether read conflict ranges are omitted for any reads done through this {@code ReadTransaction}.
	 * <br>
	 * For more information about how to use snapshot reads correctly, see
	 * <a href="/foundationdb/developer-guide.html#using-snapshot-reads" target="_blank">Using snapshot reads</a>.
	 *
	 * @return whether this is a snapshot view of the database with relaxed isolation properties
	 * @see #snapshot()
	 */
	boolean isSnapshot();

	/**
	 * Return a special-purpose, read-only view of the database. Reads done through this interface are known as "snapshot reads".
	 *  Snapshot reads selectively relax FoundationDB's isolation property, reducing
	 *  <a href="/foundationdb/developer-guide.html#transaction-conflicts" target="_blank">Transaction conflicts</a>
	 *  but making reasoning about concurrency harder.<br>
	 * <br>
	 * For more information about how to use snapshot reads correctly, see
	 * <a href="/foundationdb/developer-guide.html#using-snapshot-reads" target="_blank">Using snapshot reads</a>.
	 *
	 * @return a read-only view of this {@code ReadTransaction} with relaxed isolation properties
	 */
	ReadTransaction snapshot();

	/**
	 * Gets the version at which the reads for this {@code Transaction} will access the database.
	 * @return the version for database reads
	 */
	CompletableFuture<Long> getReadVersion();

	/**
	 * Directly sets the version of the database at which to execute reads.  The
	 *  normal operation of a transaction is to determine an appropriately recent
	 *  version; this call overrides that behavior.  If the version is set too
	 *  far in the past, {@code transaction_too_old} errors will be thrown from read operations.
	 *  <i>Infrequently used.</i>
	 *
	 * @param version the version at which to read from the database
	 */
	void setReadVersion(long version);

	/**
	 * Adds the read conflict range that this {@code ReadTransaction} would have added as if it had read
	 *  the given key range. If this is a {@linkplain #snapshot() snapshot} view of the database, this will
	 *  not add the conflict range. This mirrors how reading a range through a snapshot view
	 *  of the database does not add a conflict range for the read keys.
	 *
	 * @param keyBegin the first key in the range (inclusive)
	 * @param keyEnd the last key in the range (exclusive)
	 * @return {@code true} if the read conflict range was added and {@code false} otherwise
	 * @see Transaction#addReadConflictRange(byte[], byte[])
	 */
	boolean addReadConflictRangeIfNotSnapshot(byte[] keyBegin, byte[] keyEnd);

	/**
	 * Adds the read conflict range that this {@code ReadTransaction} would have added as if it had read
	 *  the given key. If this is a {@linkplain #snapshot() snapshot} view of the database, this will
	 *  not add the conflict range. This mirrors how reading a key through a snapshot view
	 *  of the database does not add a conflict range for the read key.
	 *
	 * @param key the key to add to the read conflict range set (it this is not a snapshot view of the database)
	 * @return {@code true} if the read conflict key was added and {@code false} otherwise
	 * @see Transaction#addReadConflictKey(byte[])
	 */
	boolean addReadConflictKeyIfNotSnapshot(byte[] key);

	/**
	 * Gets a value from the database. The call will return {@code null} if the key is not
	 *  present in the database.
	 *
	 * @param key the key whose value to fetch from the database
	 *
	 * @return a {@code CompletableFuture} which will be set to the value corresponding to
	 *  the key or to null if the key does not exist.
	 */
	CompletableFuture<byte[]> get(byte[] key);

	/**
	 * Returns the key referenced by the specified {@code KeySelector}.
	 *  By default, the key is cached for the duration of the transaction, providing
	 *  a potential performance benefit. However, the value of the key is also retrieved,
	 *  using network bandwidth. Invoking {@code setReadYourWritesDisable} will avoid
	 *  both the caching and the increased network bandwidth.
	 *
	 * @see KeySelector
	 *
	 * @param selector the relative key location to resolve
	 *
	 * @return a {@code CompletableFuture} which will be set to an absolute database key
	 */
	CompletableFuture<byte[]> getKey(KeySelector selector);

	/**
	 * Gets an ordered range of keys and values from the database. The begin
	 *  and end keys are specified by {@code KeySelector}s, with the begin
	 *  {@code KeySelector} inclusive and the end {@code KeySelector} exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(KeySelector begin, KeySelector end);

	/**
	 * Gets an ordered range of keys and values from the database. The begin
	 *  and end keys are specified by {@code KeySelector}s, with the begin
	 *  {@code KeySelector} inclusive and the end {@code KeySelector} exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results.
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(KeySelector begin, KeySelector end,
			int limit);

	/**
	 * Gets an ordered range of keys and values from the database. The begin
	 *  and end keys are specified by {@code KeySelector}s, with the begin
	 *  {@code KeySelector} inclusive and the end {@code KeySelector} exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results. If {@code reverse} is {@code true} rows
	 *  will be limited starting at the end of the range.
	 * @param reverse return results starting at the end of the range in reverse order
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(KeySelector begin, KeySelector end,
			int limit, boolean reverse);

	/**
	 * Gets an ordered range of keys and values from the database. The begin
	 *  and end keys are specified by {@code KeySelector}s, with the begin
	 *  {@code KeySelector} inclusive and the end {@code KeySelector} exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results. If {@code reverse} is {@code true} rows
	 *  will be limited starting at the end of the range.
	 * @param reverse return results starting at the end of the range in reverse order
	 * @param mode provide a hint about how the results are to be used. This
	 *  can provide speed improvements or efficiency gains based on the caller's
	 *  knowledge of the upcoming access pattern.
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(KeySelector begin, KeySelector end,
			int limit, boolean reverse, StreamingMode mode);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(byte[] begin, byte[] end);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results.
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(byte[] begin, byte[] end,
			int limit);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results. If {@code reverse} is {@code true} rows
	 *  will be limited starting at the end of the range.
	 * @param reverse return results starting at the end of the range in reverse order
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(byte[] begin, byte[] end,
			int limit, boolean reverse);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive.
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param begin the beginning of the range (inclusive)
	 * @param end the end of the range (exclusive)
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results. If {@code reverse} is {@code true} rows
	 *  will be limited starting at the end of the range.
	 * @param reverse return results starting at the end of the range in reverse order
	 * @param mode provide a hint about how the results are to be used. This
	 *  can provide speed improvements or efficiency gains based on the caller's
	 *  knowledge of the upcoming access pattern.
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(byte[] begin, byte[] end,
			int limit, boolean reverse, StreamingMode mode);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive. {@link Range}s are returned
	 *  from calls to {@link Tuple#range()} and {@link Range#startsWith(byte[])}. <br>
	 * <br>
	 * <b>Note:</b> users of older version of the API should replace old calls to
	 *  {@code getRangeStartsWith( k )} with {@code getRange(Range.startsWith( k ))}
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param range the range of keys to return
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(Range range);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive. {@link Range}s are returned
	 *  from calls to {@link Tuple#range()} and {@link Range#startsWith(byte[])}. <br>
	 * <br>
	 * <b>Note:</b> users of older version of the API should replace old calls to
	 *  {@code getRangeStartsWith( k )} with {@code getRange(Range.startsWith( k ))}
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param range the range of keys to return
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results.
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(Range range,
			int limit);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive. {@link Range}s are returned
	 *  from calls to {@link Tuple#range()} and {@link Range#startsWith(byte[])}. <br>
	 * <br>
	 * <b>Note:</b> users of older version of the API should replace old calls to
	 *  {@code getRangeStartsWith( k )} with {@code getRange(Range.startsWith( k ))}
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param range the range of keys to return
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results. If {@code reverse} is {@code true} rows
	 *  will be limited starting at the end of the range.
	 * @param reverse return results starting at the end of the range in reverse order
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(Range range,
			int limit, boolean reverse);

	/**
	 * Gets an ordered range of keys and values from the database.  The begin
	 *  and end keys are specified by {@code byte[]} arrays, with the begin
	 *  key inclusive and the end key exclusive. {@link Range}s are returned
	 *  from calls to {@link Tuple#range()} and {@link Range#startsWith(byte[])}. <br>
	 * <br>
	 * <b>Note:</b> users of older version of the API should replace old calls to
	 *  {@code getRangeStartsWith( k )} with {@code getRange(Range.startsWith( k ))}
	 *
	 * @see KeySelector
	 * @see AsyncIterator
	 *
	 * @param range the range of keys to return
	 * @param limit the maximum number of results to return. Limits results to the
	 *  <i>first</i> keys in the range. Pass {@link #ROW_LIMIT_UNLIMITED} if this query
	 *  should not limit the number of results. If {@code reverse} is {@code true} rows
	 *  will be limited starting at the end of the range.
	 * @param reverse return results starting at the end of the range in reverse order
	 * @param mode provide a hint about how the results are to be used. This
	 *  can provide speed improvements or efficiency gains based on the caller's
	 *  knowledge of the upcoming access pattern.
	 *
	 * @return a handle to access the results of the asynchronous call
	 */
	AsyncIterable<KeyValue> getRange(Range range,
			int limit, boolean reverse, StreamingMode mode);

	/**
	 * Returns a set of options that can be set on a {@code Transaction}
	 *
	 * @return a set of transaction-specific options affecting this {@code Transaction}
	 */
	TransactionOptions options();
}
