/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.nio.channels.*
import kotlin.coroutines.*

internal abstract class NIOSocketImpl<out S>(
    override val channel: S,
    val selector: SelectorManager,
    private val socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : ReadWriteSocket, SelectableBase(channel), CoroutineScope
    where S : java.nio.channels.ByteChannel, S : SelectableChannel {

    private val closeFlag = atomic(false)

    private val readerJob = atomic<ReaderJob?>(null)

    private val writerJob = atomic<WriterJob?>(null)

    override val socketContext: CompletableJob = Job()

    override val coroutineContext: CoroutineContext
        get() = socketContext

    override fun attachForReading(): ByteReadChannel = SocketReadChannel(channel, this, selector, socketOptions)

    override fun attachForWriting(): ByteWriteChannel = SocketWriteChannel(channel, this, selector, socketOptions)

    override fun dispose() {
        close()
    }

    override fun close() {
        if (!closeFlag.compareAndSet(false, true)) return
        checkChannels()
    }

    private fun actualClose(): Throwable? {
        return try {
            channel.close()
            super.close()
            null
        } catch (cause: Throwable) {
            cause
        } finally {
            selector.notifyClosed(this)
        }
    }

    private fun checkChannels() {
        if (closeFlag.value && readerJob.value.completedOrNotStarted && writerJob.value.completedOrNotStarted) {
            val e1 = readerJob.value.exception
            val e2 = writerJob.value.exception
            val e3 = actualClose()

            val combined = combine(combine(e1, e2), e3)

            if (combined == null) socketContext.complete() else socketContext.completeExceptionally(combined)
        }
    }

    private fun combine(e1: Throwable?, e2: Throwable?): Throwable? = when {
        e1 == null -> e2
        e2 == null -> e1
        e1 === e2 -> e1
        else -> {
            e1.addSuppressed(e2)
            e1
        }
    }

    private val ChannelJob?.completedOrNotStarted: Boolean
        get() = this == null || this.job.isCompleted

    @OptIn(InternalCoroutinesApi::class)
    private val ChannelJob?.exception: Throwable?
        get() = this?.takeIf { it.job.isCancelled }
            ?.job?.getCancellationException()?.cause // TODO it should be completable deferred or provide its own exception
}
