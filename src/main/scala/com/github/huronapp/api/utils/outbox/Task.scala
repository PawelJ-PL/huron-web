package com.github.huronapp.api.utils.outbox

import io.chrisdavenport.fuuid.FUUID

final case class Task(taskId: FUUID, command: OutboxCommand)
