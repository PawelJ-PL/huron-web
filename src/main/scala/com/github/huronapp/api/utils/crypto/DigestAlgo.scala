package com.github.huronapp.api.utils.crypto

import java.security.MessageDigest

sealed trait DigestAlgo {

  val instance: MessageDigest

}

object DigestAlgo {

  case object Sha256 extends DigestAlgo {

    override val instance: MessageDigest = MessageDigest.getInstance("SHA-256")

  }

}
