/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.network.sockets

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*

/**
 * This exception is thrown in case connect timeout exceeded.
 */
public expect class ConnectTimeoutException(message: String, cause: Throwable? = null) : IOException

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
public expect class SocketTimeoutException(message: String, cause: Throwable? = null) : IOException

/**
 * Returns [ByteReadChannel] with [ByteChannel.close] handler that returns [SocketTimeoutException] instead of
 * [SocketTimeoutException].
 */
@InternalAPI
public fun CoroutineScope.mapEngineExceptions(input: ByteReadChannel, request: HttpRequestData): ByteReadChannel {
    return input
}

/**
 * Returns [ByteWriteChannel] with [ByteChannel.close] handler that returns [SocketTimeoutException] instead of
 * [SocketTimeoutException].
 */
@InternalAPI
public fun CoroutineScope.mapEngineExceptions(output: ByteWriteChannel, request: HttpRequestData): ByteWriteChannel {
    return output
}

/**
 * Creates [ByteChannel] that maps close exceptions (close the channel with [SocketTimeoutException] if asked to
 * close it with [SocketTimeoutException]).
 */
@Suppress("DEPRECATION")
internal expect fun ByteChannelWithMappedExceptions(request: HttpRequestData): ByteChannel
