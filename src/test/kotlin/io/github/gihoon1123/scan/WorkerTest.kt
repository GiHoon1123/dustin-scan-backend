package io.github.gihoon1123.scan

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerTest {
    @Test
    fun `sync retries the same uncommitted height on the next tick`() {
        val store = mockk<ExplorerStore>()
        val chain = mockk<ChainClient>()
        every { store.one(any()) } returns mapOf("number" to "3")
        every { chain.block(4) } returns mapOf("number" to "0x4", "hash" to "b4")
        every { chain.requestUrl(any(), any(), any()) } returnsMany listOf(mapOf("success" to false, "error" to "database failed"), mapOf("success" to true))
        val syncer = ChainSyncer(store, chain, "http://localhost:4001")
        syncer.sync()
        syncer.sync()
        verify(exactly = 2) { chain.block(4) }
        verify(exactly = 2) { chain.requestUrl("http://localhost:4001/indexer/process-block", any(), "POST") }
    }

    @Test
    fun `sync stops quietly at a block that has not been produced`() {
        val store = mockk<ExplorerStore>()
        val chain = mockk<ChainClient>()
        every { store.one(any()) } returns null
        every { chain.block(0) } returns null
        ChainSyncer(store, chain, "http://localhost:4001").sync()
        verify(exactly = 0) { chain.requestUrl(any(), any(), any()) }
    }

    @Test
    fun `indexer reports failure instead of claiming partial success`() {
        val indexer = mockk<BlockIndexer>()
        every { indexer.index(any()) } throws IllegalStateException("database failed")
        val response = IndexerController(indexer).process(mapOf("number" to "0x4"))
        assertEquals(false, response["success"])
        assertEquals(4L, response["blockNumber"])
        assertEquals("database failed", response["error"])
    }
}
