package com.github.huronapp.api.testdoubles

import com.github.huronapp.api.messagebus.InternalMessage
import zio.{Has, Hub, Ref, ZLayer}

object InternalTopicFake {

  def usingRef(ref: Ref[List[InternalMessage]]): ZLayer[Any, Nothing, Has[Hub[InternalMessage]]] =
    Hub.bounded[InternalMessage](16).map(_.contramapM((message: InternalMessage) => ref.update(_ :+ message).as(message))).toLayer

}
