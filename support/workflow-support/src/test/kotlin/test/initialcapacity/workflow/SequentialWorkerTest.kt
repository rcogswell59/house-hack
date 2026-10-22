package test.initialcapacity.workflow

import io.initialcapacity.workflow.CollectorTask
import io.initialcapacity.workflow.SequentialWorker
import io.initialcapacity.workflow.Worker
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SequentialWorkerTest {
    private val log = mutableListOf<String>()
    private fun step(name: String, fail: Boolean = false) = object : Worker<CollectorTask> {
        override val name = name
        override fun execute(task: CollectorTask) { log += name; if (fail) throw IllegalStateException("$name failed") }
    }
    private val task = CollectorTask("daily-pipeline", LocalDate.of(2026, 10, 21))

    @Test
    fun runs_steps_in_order() {
        SequentialWorker("daily-pipeline", listOf(step("collect"), step("link"), step("rents"), step("sweep"))).execute(task)

        assertEquals(listOf("collect", "link", "rents", "sweep"), log)
    }

    @Test
    fun a_failing_step_stops_the_pipeline_and_propagates() {
        assertFailsWith<IllegalStateException> {
            SequentialWorker("daily-pipeline", listOf(step("collect"), step("link", fail = true), step("rents"))).execute(task)
        }
        assertEquals(listOf("collect", "link"), log)
    }
}
