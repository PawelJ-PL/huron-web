package com.github.huronapp.api.messagebus.handlers

import com.github.huronapp.api.constants.{Files, MiscConstants}
import com.github.huronapp.api.testdoubles.{Failed, FileSystemServiceFake, KamonTracingFake, NotStarted, OutboxServiceFake, StoredTask}
import com.github.huronapp.api.utils.outbox.{OutboxCommand, Task}
import zio.Ref
import zio.clock.Clock
import zio.logging.slf4j.Slf4jLogger
import zio.test.Assertion.{hasSameElements, isEmpty}
import zio.test.assert
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}

object OutboxTasksHandlerSpec extends DefaultRunnableSpec with Files with MiscConstants {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Outbox tasks handler spec")(
      deleteFilesTaskSuccessTest,
      deleteFilesTaskFailureTest
    )

  private val logger = Slf4jLogger.make((_, str) => str)

  private def env(tasks: Ref[List[StoredTask]], files: Ref[Map[String, Array[Byte]]]) =
    logger ++ Clock.any ++ KamonTracingFake.noOp ++ OutboxServiceFake.create(tasks) ++ FileSystemServiceFake.create(files)

  private val deleteFilesTaskSuccessTest = testM("should delete file and mark task as finished") {
    val task = Task(ExampleFuuid1, OutboxCommand.DeleteFiles(List("foo/bar", "quux/quuz"), recursively = true))

    val fileSystem = Map(
      "foo/bar" -> ExampleFileContent,
      "baz/qux" -> ExampleFileContent,
      "quux/quuz" -> ExampleFileContent,
      "corge/grault" -> ExampleFileContent
    )

    for {
      tasks      <- Ref.make(List(StoredTask(task, NotStarted)))
      files      <- Ref.make(fileSystem)
      _          <- OutboxTasksHandler.process(task).provideLayer(env(tasks, files))
      finalTasks <- tasks.get
      finalFiles <- files.get
    } yield assert(finalTasks)(isEmpty) &&
      assert(finalFiles.keySet)(hasSameElements(Set("baz/qux", "corge/grault")))
  }

  private val deleteFilesTaskFailureTest = testM("should mark task as failed") {
    val task = Task(ExampleFuuid1, OutboxCommand.DeleteFiles(List("foo/bar", "quux/quuz", "other"), recursively = false))

    val fileSystem = Map(
      "foo/bar" -> ExampleFileContent,
      "baz/qux" -> ExampleFileContent,
      "quux/quuz" -> ExampleFileContent,
      "corge/grault" -> ExampleFileContent
    )

    for {
      tasks      <- Ref.make(List(StoredTask(task, NotStarted)))
      files      <- Ref.make(fileSystem)
      _          <- OutboxTasksHandler.process(task).provideLayer(env(tasks, files))
      finalTasks <- tasks.get
      finalFiles <- files.get
    } yield assert(finalTasks)(hasSameElements(List(StoredTask(task, Failed("other"))))) &&
      assert(finalFiles.keySet)(hasSameElements(Set("baz/qux", "corge/grault")))
  }

}
