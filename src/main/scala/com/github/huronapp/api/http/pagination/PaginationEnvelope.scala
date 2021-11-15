package com.github.huronapp.api.http.pagination

final case class PaginationEnvelope[A](data: List[A], totalResults: Long)
