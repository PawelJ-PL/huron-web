package com.github.huronapp.api.domain.collections

import enumeratum._

sealed trait CollectionPermission extends EnumEntry

object CollectionPermission extends Enum[CollectionPermission] {

  override def values = findValues

  case object ManageCollection extends CollectionPermission

  case object CreateFile extends CollectionPermission

  case object ModifyFile extends CollectionPermission

  case object ReadFile extends CollectionPermission

  case object ReadFileMetadata extends CollectionPermission

}
