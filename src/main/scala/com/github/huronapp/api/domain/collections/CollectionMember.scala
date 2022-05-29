package com.github.huronapp.api.domain.collections

import com.github.huronapp.api.domain.users.UserId

final case class CollectionMember(collectionId: CollectionId, userId: UserId, permissions: List[CollectionPermission])
