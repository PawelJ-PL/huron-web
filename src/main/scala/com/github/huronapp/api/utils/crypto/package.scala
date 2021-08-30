package com.github.huronapp.api.utils

package object crypto {

  final val EncryptedStringPattern =
    "^AES-CBC:[0-9a-zA-Z]+:[0-9a-zA-Z]+$"

  final val EncryptedStringPatternWithKeyId =
    "^AES-CBC:[0-9a-zA-Z]+:[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}:[0-9a-zA-Z]+$"

}
