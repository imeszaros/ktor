/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlin.test.*

class ChannelTest {

    @Test
    fun testCopyToFlushesDestination() = testSuspend {
        val source = ByteChannel()
        val destination = ByteChannel()

        launch(Dispatchers.Unconfined) {
            source.copyTo(destination)
        }

        launch(Dispatchers.Unconfined) {
            source.writeByte(1)
            source.flush()
        }

        val byte = destination.readByte()
        assertEquals(1, byte)
        source.flushAndClose()
    }

    @Test
    fun testCopyToBoth() = testSuspend {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(first, second)

        launch(Dispatchers.Unconfined) {
            source.writeFully(data)
            source.flushAndClose()
        }

        val firstResult = async(Dispatchers.Unconfined) {
            first.readRemaining().readBytes()
        }
        val secondResult = async(Dispatchers.Unconfined) {
            second.readRemaining().readBytes()
        }

        val results = listOf(firstResult, secondResult).awaitAll()
        assertArrayEquals(data, results[0])
        assertArrayEquals(data, results[1])
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testCopyToBothCancelSource() = testSuspend {
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(first, second)

        val message = "Expected reason"

        launch(Dispatchers.Unconfined) {
            source.cancel(IllegalStateException(message))
        }

        assertFailsWith<IOException> {
            val firstResult = GlobalScope.async(Dispatchers.Unconfined) {
                first.readRemaining().readBytes()
            }
            firstResult.await()
        }

        assertFailsWith<IOException> {
            val secondResult = GlobalScope.async(Dispatchers.Unconfined) {
                second.readRemaining().readBytes()
            }
            secondResult.await()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testCopyToBothCancelFirstReader() = testSuspend {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(first, second)

        val message = "Expected reason"

        first.cancel(IllegalStateException(message))

        val sourceResult = GlobalScope.async(Dispatchers.Unconfined) {
            source.writeFully(data)
            source.flushAndClose()
        }

        sourceResult.await()

        assertFailsWith<IOException> {
            val secondResult = GlobalScope.async(Dispatchers.Unconfined) {
                second.readRemaining().readBytes()
            }
            secondResult.await()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testCopyToBothCancelSecondReader() = testSuspend {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val source = ByteChannel()
        val first = ByteChannel()
        val second = ByteChannel()

        source.copyToBoth(second, first)

        val message = "Expected reason"

        val sourceResult = GlobalScope.async(Dispatchers.Unconfined) {
            source.writeFully(data)
            source.flushAndClose()
        }

        first.cancel(IllegalStateException(message))

        val secondResult = GlobalScope.async(Dispatchers.Unconfined) {
            second.readRemaining().readBytes()
        }
        secondResult.await()
        sourceResult.await()
    }
}

private inline fun assertFailsWithMessage(message: String, block: () -> Unit) {
    var fail = false
    try {
        block()
    } catch (cause: Throwable) {
        assertEquals(message, cause.message)
        fail = true
    }

    assertTrue(fail)
}

private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
    assertTrue { expected.contentEquals(actual) }
}
