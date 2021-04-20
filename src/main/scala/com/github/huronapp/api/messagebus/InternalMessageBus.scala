package com.github.huronapp.api.messagebus

import zio.{Has, Hub, ZLayer}

object InternalMessageBus {

  val live: ZLayer[Any, Nothing, Has[Hub[InternalMessage]]] = Hub.bounded[InternalMessage](16).toLayer

}
