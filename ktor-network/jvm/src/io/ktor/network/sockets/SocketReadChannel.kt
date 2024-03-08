/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.io.*
import java.nio.channels.*

internal class SocketReadChannel(
    private val channel: ReadableByteChannel,
    private val selectable: Selectable,
    private val selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions?
) : ByteReadChannel {
    @Volatile
    private var closed: ClosedToken? = null

    private val buffer = Buffer()

    override val closedCause: Throwable?
        get() = closed?.cause

    override val isClosedForRead: Boolean
        get() = closed != null && buffer.exhausted()

    init {
        selectable.interestOp(SelectInterest.READ, true)
    }

    @InternalAPI
    override val readBuffer: Source get() = buffer

    override suspend fun awaitContent(): Boolean {
        if (closed != null) {
            closedCause?.let { throw it }
        }

        var count = 0
        while (count == 0) {
            count = channel.read(buffer)
            if (count == -1) {
                closed = CLOSED_OK
                return false
            }
            if (count == 0) {
                selector.select(selectable, SelectInterest.READ)
            }
        }

        return closed == null
    }

    override fun cancel(cause: Throwable) {
        buffer.close()
        selectable.interestOp(SelectInterest.READ, false)
        channel.close()

        if (closed != null) return
        closed = ClosedToken(IOException("Channel was cancelled", cause))
    }
}
