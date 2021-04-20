package com.github.huronapp.api.config

import ciris.{ConfigKey, ConfigValue}

object ConfigSource {

  def runtime[A](extract: Runtime => A): ConfigValue[A] =
    ConfigValue.suspend {
      val key = ConfigKey("Java runtime")
      val value = extract(Runtime.getRuntime)

      if (value != null) ConfigValue.loaded(key, value) else ConfigValue.missing(key)
    }

}
