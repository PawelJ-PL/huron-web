package com.github.huronapp.api.utils.outbox

import com.github.huronapp.api.config.OutboxTasksConfig
import zio.{Has, Queue, ZLayer}

object OutboxTaskProcessingQueue {

  val live: ZLayer[Has[OutboxTasksConfig], Nothing, Has[Queue[Task]]] =
    ZLayer.fromServiceM[OutboxTasksConfig, Any, Nothing, Queue[Task]](config => Queue.bounded[Task](config.taskQueueSize))

}
