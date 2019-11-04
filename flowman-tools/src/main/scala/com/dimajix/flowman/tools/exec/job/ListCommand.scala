package com.dimajix.flowman.tools.exec.job

import org.slf4j.LoggerFactory

import com.dimajix.flowman.execution.Context
import com.dimajix.flowman.execution.Executor
import com.dimajix.flowman.spec.Project
import com.dimajix.flowman.tools.exec.ActionCommand


class ListCommand extends ActionCommand {
    private val logger = LoggerFactory.getLogger(classOf[ListCommand])

    override def executeInternal(executor:Executor, context:Context, project: Project) : Boolean = {
        project.jobs.keys.foreach(println)
        true
    }

}