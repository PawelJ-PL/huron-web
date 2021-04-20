package com.github.huronapp.api.utils.tracing

import kamon.Kamon
import zio.Has
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform.defaultYieldOpCount
import zio.internal.{Executor, Platform}
import zio.random.Random
import zio.system.System

trait ZioKamonApp extends zio.App {
  Kamon.init()



  private val defaultEcWithKamon: ContextAwareExecutionContext = ContextAwareExecutionContext(Platform.default.executor.asEC)

  override def platform: Platform =
    Platform.default.withExecutor(Executor.fromExecutionContext(defaultYieldOpCount)(defaultEcWithKamon))


  private val defaultBlockingExecutor = Blocking.Service.live.blockingExecutor

  private val blockingEcWithKamon = ContextAwareExecutionContext(defaultBlockingExecutor.asEC)

  private val blockingWithKamon = new Blocking.Service {

    override def blockingExecutor: Executor = Executor.fromExecutionContext(defaultBlockingExecutor.yieldOpCount)(blockingEcWithKamon)

  }

  override def environment: zio.ZEnv =
    Has.allOf[Clock.Service, Console.Service, System.Service, Random.Service, Blocking.Service](
      Clock.Service.live,
      Console.Service.live,
      System.Service.live,
      Random.Service.live,
      blockingWithKamon
    )

}
