package com.github.huronapp.api.utils

import com.github.huronapp.api.testdoubles.KamonTracingFake
import com.github.huronapp.api.utils.FileSystemService.FileSystemService
import zio.blocking.Blocking
import zio.nio.core.file.Path
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.{ZIO, ZLayer, ZManaged}
import zio.test.assert
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec}

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.reflect.io.Directory

object LocalFilesystemServiceSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Any] =
    suite("Local filesystem service spec")(
      saveAndReadFileTest,
      fileOverwriteTest,
      saveAndReadLargeFileTest,
      readNonExistingFileTest,
      deleteFileTest
    )

  private val env: ZManaged[Any, Throwable, ZLayer[Any, Throwable, FileSystemService]] = ZManaged
    .make(ZIO.effect(Files.createTempDirectory("testfs"))) { d =>
      ZIO.effect {
        val dir = new Directory(d.toFile)
        dir.deleteRecursively()
      }.orDie
    }
    .map(dir => Blocking.live ++ KamonTracingFake.noOp >>> FileSystemService.localFs(Path.fromJava(dir), createDirs = false))

  private def withEnv[E, A](operation: ZIO[FileSystemService, Any, A]): ZIO[Any, Any, A] =
    env.use(service => operation.provideLayer(service))

  private val saveAndReadFileTest = testM("should write and read file content") {
    val textContent = "Zażółć gęślą jaźń"

    withEnv(
      for {
        _      <- FileSystemService.saveFile("foo/bar.txt", textContent.getBytes(StandardCharsets.UTF_8))
        result <- FileSystemService.readFile("foo/bar.txt")
        resultContent = result.map(bytes => new String(bytes, StandardCharsets.UTF_8))
      } yield assert(resultContent)(isSome(equalTo(textContent)))
    )
  }

  private val fileOverwriteTest = testM("should overwrite file content") {
    val firstContent = "Zażółć gęślą jaźń"
    val secondContent =
      """foo
      |bar
      |baz
      |""".stripMargin

    withEnv(
      for {
        _      <- FileSystemService.saveFile("foo/bar.txt", firstContent.getBytes(StandardCharsets.UTF_8), allowOverwrite = true)
        _      <- FileSystemService.saveFile("foo/bar.txt", secondContent.getBytes(StandardCharsets.UTF_8), allowOverwrite = true)
        result <- FileSystemService.readFile("foo/bar.txt")
        resultContent = result.map(bytes => new String(bytes, StandardCharsets.UTF_8))
      } yield assert(resultContent)(isSome(equalTo(secondContent)))
    )
  }

  private val saveAndReadLargeFileTest = testM("should write and read large file content") {
    val textContent = "Zażółć gęślą jaźń" * 10240

    withEnv(
      for {
        _      <- FileSystemService.saveFile("foo/bar.txt", textContent.getBytes(StandardCharsets.UTF_8))
        result <- FileSystemService.readFile("foo/bar.txt")
        resultContent = result.map(bytes => new String(bytes, StandardCharsets.UTF_8))
      } yield assert(resultContent)(isSome(equalTo(textContent)))
    )
  }

  private val readNonExistingFileTest = testM("should return None when reading non existing file") {
    withEnv(
      for {
        result <- FileSystemService.readFile("foo/bar.txt")
      } yield assert(result)(isNone)
    )
  }

  private val deleteFileTest = testM("should delete file") {
    val textContent = "Zażółć gęślą jaźń"

    withEnv(
      for {
        _      <- FileSystemService.saveFile("foo/bar.txt", textContent.getBytes(StandardCharsets.UTF_8))
        _      <- FileSystemService.deleteFileOrDirectory("foo/bar.txt", recursively = false)
        result <- FileSystemService.readFile("foo/bar.txt")
      } yield assert(result)(isNone)
    )
  }

}
