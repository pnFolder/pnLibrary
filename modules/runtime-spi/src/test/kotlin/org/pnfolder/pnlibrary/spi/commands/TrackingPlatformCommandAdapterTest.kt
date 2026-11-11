package org.pnfolder.pnlibrary.spi.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TrackingPlatformCommandAdapterTest {

    @Test
    fun `registration created while closing is released after registration`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closes = AtomicInteger()
        val adapter = object : TrackingPlatformCommandAdapter() {
            override fun registerNative(
                owner: Any,
                command: CommandDefinition,
                dispatcher: PlatformCommandDispatcher,
            ): PlatformCommandRegistration {
                entered.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                return PlatformCommandRegistration { closes.incrementAndGet() }
            }
        }
        val executor = Executors.newSingleThreadExecutor()

        try {
            val future = executor.submit<PlatformCommandRegistration> {
                adapter.register(Any(), CommandDefinition.builder("test").build(), dispatcher())
            }
            check(entered.await(2, TimeUnit.SECONDS))
            adapter.close()
            release.countDown()

            future.get(2, TimeUnit.SECONDS)
            assertEquals(1, closes.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
            adapter.close()
        }
    }

    private fun dispatcher() = object : PlatformCommandDispatcher {
        override fun execute(
            command: CommandDefinition,
            context: CommandContext,
        ) = org.pnfolder.pnlibrary.api.commands.completedExecution()

        override fun suggest(
            command: CommandDefinition,
            context: CommandContext,
        ) = org.pnfolder.pnlibrary.api.commands.completedSuggestions(emptyList())
    }
}
