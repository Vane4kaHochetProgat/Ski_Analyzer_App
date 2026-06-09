package com.vane4ka.skianalyzer

import android.content.Context
import com.vane4ka.skianalyzer.analysis.AnalysisOrchestrator
import com.vane4ka.skianalyzer.analysis.AnalysisOutcome
import com.vane4ka.skianalyzer.analysis.AnalysisTimingLog
import com.vane4ka.skianalyzer.analysis.VideoAnalyzer
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisOrchestratorTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private lateinit var connectivity: ConnectivityObserver
    private lateinit var session: UserSession
    private lateinit var serverAnalyzer: VideoAnalyzer
    private lateinit var localAnalyzer: VideoAnalyzer

    private val file = File("/tmp/clip.mp4")
    private val sampleResult = AnalysisResult(
        status = "completed",
        analysis = Analysis(
            overall_score = 80.0,
            angle_analysis = emptyList(),
            recommendations = emptyList(),
        ),
        files = Files(annotated_video = null, charts = null),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        connectivity = mockk()
        session = mockk()
        serverAnalyzer = mockk()
        localAnalyzer = mockk()
        mockkObject(AnalysisTimingLog)
        every { AnalysisTimingLog.append(any(), any(), any(), any()) } just Runs
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkObject(AnalysisTimingLog)
    }

    private fun orchestrator() = AnalysisOrchestrator(
        context = context,
        connectivity = connectivity,
        session = session,
        serverAnalyzer = serverAnalyzer,
        localAnalyzer = localAnalyzer,
    )

    @Test
    fun `online and server success returns Done with SERVER source`() = runTest(dispatcher) {
        every { connectivity.isOnlineNow() } returns true
        coEvery { serverAnalyzer.analyze(file) } returns sampleResult

        val outcome = orchestrator().analyze(file)

        assertTrue("expected Done, got $outcome", outcome is AnalysisOutcome.Done)
        val done = outcome as AnalysisOutcome.Done
        assertEquals(AnalysisOutcome.Source.SERVER, done.source)
        assertEquals(sampleResult, done.result)
        coVerify(exactly = 0) { localAnalyzer.analyze(any()) }
    }

    @Test
    fun `online but server fails falls back to local when offline mode is RUN_LOCALLY`() = runTest(dispatcher) {
        every { connectivity.isOnlineNow() } returns true
        coEvery { serverAnalyzer.analyze(file) } throws IOException("server down")
        every { session.offlineMode } returns flowOf(OfflineMode.RUN_LOCALLY)
        coEvery { localAnalyzer.analyze(file) } returns sampleResult

        val outcome = orchestrator().analyze(file)

        assertTrue(outcome is AnalysisOutcome.Done)
        assertEquals(AnalysisOutcome.Source.LOCAL, (outcome as AnalysisOutcome.Done).source)
        coVerify(exactly = 1) { serverAnalyzer.analyze(file) }
        coVerify(exactly = 1) { localAnalyzer.analyze(file) }
    }

    @Test
    fun `online server fail then local fail returns Failed with reason`() = runTest(dispatcher) {
        every { connectivity.isOnlineNow() } returns true
        coEvery { serverAnalyzer.analyze(file) } throws IOException("server")
        every { session.offlineMode } returns flowOf(OfflineMode.RUN_LOCALLY)
        coEvery { localAnalyzer.analyze(file) } throws RuntimeException("tflite crashed")

        val outcome = orchestrator().analyze(file)

        assertTrue(outcome is AnalysisOutcome.Failed)
        val msg = (outcome as AnalysisOutcome.Failed).message
        assertTrue("expected mention of tflite, got: $msg", msg.contains("tflite crashed"))
    }

    @Test
    fun `offline with RUN_LOCALLY skips server and runs local analyzer`() = runTest(dispatcher) {
        every { connectivity.isOnlineNow() } returns false
        every { session.offlineMode } returns flowOf(OfflineMode.RUN_LOCALLY)
        coEvery { localAnalyzer.analyze(file) } returns sampleResult

        val outcome = orchestrator().analyze(file)

        assertTrue(outcome is AnalysisOutcome.Done)
        assertEquals(AnalysisOutcome.Source.LOCAL, (outcome as AnalysisOutcome.Done).source)
        coVerify(exactly = 0) { serverAnalyzer.analyze(any()) }
    }

    @Test
    fun `offline with RUN_LOCALLY and local fail returns Failed`() = runTest(dispatcher) {
        every { connectivity.isOnlineNow() } returns false
        every { session.offlineMode } returns flowOf(OfflineMode.RUN_LOCALLY)
        coEvery { localAnalyzer.analyze(file) } throws RuntimeException("frames empty")

        val outcome = orchestrator().analyze(file)

        assertTrue(outcome is AnalysisOutcome.Failed)
    }

    @Test
    fun `analyze records timing entry with DONE_SERVER tag on success`() = runTest(dispatcher) {
        every { connectivity.isOnlineNow() } returns true
        coEvery { serverAnalyzer.analyze(file) } returns sampleResult

        orchestrator().analyze(file)

        verify { AnalysisTimingLog.append(context, "DONE_SERVER", file, any()) }
    }

    @Test
    fun `analyze records timing entry with FAILED tag when local fails`() = runTest(dispatcher) {
        every { connectivity.isOnlineNow() } returns false
        every { session.offlineMode } returns flowOf(OfflineMode.RUN_LOCALLY)
        coEvery { localAnalyzer.analyze(file) } throws RuntimeException("x")

        orchestrator().analyze(file)

        verify { AnalysisTimingLog.append(context, "FAILED", file, any()) }
    }
}
