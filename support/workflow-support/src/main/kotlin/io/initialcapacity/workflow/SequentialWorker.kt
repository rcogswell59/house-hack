package io.initialcapacity.workflow

import org.slf4j.LoggerFactory

/** Runs [steps] in order for one task; a step that throws stops the pipeline so the task is not marked complete. */
class SequentialWorker<T>(override val name: String, private val steps: List<Worker<T>>) : Worker<T> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun execute(task: T) {
        steps.forEachIndexed { i, step ->
            logger.info("{} step {}/{}: {}", name, i + 1, steps.size, step.name)
            step.execute(task)
        }
    }
}
