package com.github.huronapp.api.utils

import com.github.huronapp.api.utils.tracing.KamonTracing
import com.github.huronapp.api.utils.tracing.KamonTracing.KamonTracing
import zio.blocking.Blocking
import zio.nio.core.file.Path
import zio.{Chunk, Has, ZIO, ZLayer}
import zio.macros.accessible
import zio.nio.channels.AsynchronousFileChannel
import zio.nio.file.Files

import java.io.FileNotFoundException
import java.nio.file.{NoSuchFileException, StandardOpenOption}
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.util

@accessible
object FileSystemService {

  type FileSystemService = Has[FileSystemService.Service]

  trait Service {

    def saveFile(path: String, content: Array[Byte], allowOverwrite: Boolean = false): ZIO[Any, Throwable, Unit]

    def readFile(path: String): ZIO[Any, Throwable, Option[Array[Byte]]]

    def deleteFileOrDirectory(path: String, recursively: Boolean): ZIO[Any, Throwable, Unit]

  }

  def localFs(baseDir: Path, createDirs: Boolean): ZLayer[Blocking with KamonTracing, Throwable, FileSystemService] = {
    val defaultBaseDirAttributes: util.EnumSet[PosixFilePermission] =
      util.EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)

    ZLayer.fromServicesM[Blocking.Service, KamonTracing.Service, Blocking, Throwable, FileSystemService.Service]((blocking, tracing) =>
      Files
        .exists(baseDir)
        .tap(exists =>
          Files.createDirectories(baseDir, PosixFilePermissions.asFileAttribute(defaultBaseDirAttributes)).when(!exists && createDirs)
        ) *>
        Files
          .isDirectory(baseDir)
          .tap(isDir => ZIO.fail(new FileNotFoundException(s"Directory $baseDir not found")).unless(isDir))
          .as(new Service {

            final val MaxReadPartSize: Int = 8 * 1024

            private def mergePath(path: String) =
              path.split("/").toList match {
                case ::(head, next) => baseDir / Path(head, next: _*)
                case Nil            => baseDir
              }

            private def withChannel[A](
              path: Path,
              options: List[StandardOpenOption],
              operation: AsynchronousFileChannel => ZIO[Any, Throwable, A]
            ) =
              ZIO
                .runtime
                .map((runtime: zio.Runtime[Any]) => runtime.platform.executor.asECES)
                .flatMap(eces =>
                  AsynchronousFileChannel
                    .openWithExecutor(path, options.toSet, Some(eces))
                    .use(operation)
                )

            override def saveFile(path: String, content: Array[Byte], allowOverwrite: Boolean = false): ZIO[Any, Throwable, Unit] = {
              val chunk = Chunk.fromArray(content)
              val baseOptions =
                List(StandardOpenOption.READ, StandardOpenOption.WRITE)
              val options =
                if (allowOverwrite) baseOptions.appendedAll(List(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
                else baseOptions.appended(StandardOpenOption.CREATE_NEW)

              val mergedPath = mergePath(path)

              tracing.preserveContext(
                tracing.createSpan(
                  "Save local file",
                  ZIO
                    .foreach_(mergedPath.parent)(parent =>
                      Files
                        .createDirectories(parent, PosixFilePermissions.asFileAttribute(defaultBaseDirAttributes))
                        .provideLayer(ZLayer.succeed(blocking))
                    ) *>

                    withChannel(mergedPath, options, _.writeChunk(chunk, 0L).unit),
                  Map("file.size" -> content.length.toString)
                )
              )
            }

            override def readFile(path: String): ZIO[Any, Throwable, Option[Array[Byte]]] = {
              val mergedPath = mergePath(path)

              tracing.preserveContext(
                tracing.createSpan(
                  "Read local file",
                  withChannel(
                    mergedPath,
                    List(StandardOpenOption.READ),
                    channel => channel.size.flatMap(size => readParts(size, channel, 0L, Array.empty[Byte]).map(Some(_)))
                  ).catchSome {
                    case _: NoSuchFileException => ZIO.none
                  }
                )
              )
            }

            private def readParts(size: Long, channel: AsynchronousFileChannel, currentPosition: Long, currentContent: Array[Byte])
              : ZIO[Any, Exception, Array[Byte]] =
              if (size < 1)
                ZIO.succeed(currentContent)
              else {
                val capacity = Math.min(size, MaxReadPartSize.toLong).toInt
                val restSize = size - capacity
                channel
                  .readChunk(capacity, currentPosition)
                  .flatMap(updated => readParts(restSize, channel, currentPosition + capacity, currentContent ++ updated.toArray[Byte]))
              }

            override def deleteFileOrDirectory(path: String, recursively: Boolean): ZIO[Any, Throwable, Unit] = {
              val mergedPath = mergePath(path)
              tracing.preserveContext(
                tracing.createSpan(
                  "Delete file or directory",
                  (if (recursively) Files.deleteRecursive(mergedPath).catchSome { case _: NoSuchFileException => ZIO.succeed(0) }
                   else Files.deleteIfExists(mergedPath))
                    .provideLayer(ZLayer.succeed(blocking))
                    .unit,
                  Map.empty
                )
              )
            }
          })
    )
  }

}
