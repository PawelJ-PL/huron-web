package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.utils.FileSystemService
import com.github.huronapp.api.utils.FileSystemService.FileSystemService
import zio.{Ref, ULayer, ZIO, ZLayer}

import java.nio.file.{FileAlreadyExistsException, NoSuchFileException}

object FileSystemServiceFake {

  def create(state: Ref[Map[String, Array[Byte]]]): ULayer[FileSystemService] =
    ZLayer.succeed(new FileSystemService.Service {

      override def saveFile(path: String, content: Array[Byte], allowOverwrite: Boolean): ZIO[Any, Throwable, Unit] =
        state.get.flatMap(s => ZIO.fail(new FileAlreadyExistsException(path)).when(s.contains(path))) *> state.update(prev =>
          prev + (path -> content)
        )

      override def readFile(path: String): ZIO[Any, Throwable, Option[Array[Byte]]] = state.get.map(_.get(path))

      override def deleteFileOrDirectory(path: String, recursively: Boolean): ZIO[Any, Throwable, Unit] =
        state.get.flatMap(files => ZIO.fail(new NoSuchFileException(path)).when(!files.contains(path) && !recursively)) *>
          state.update(_.removed(path))

    })

}
