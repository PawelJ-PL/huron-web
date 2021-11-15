package com.github.huronapp.api.http.pagination

import sttp.tapir._

final case class PagingResponseMetadata(currentPage: Int, limit: Int, totalPages: Int, prevPage: Option[Int], nextPage: Option[Int])

object PagingResponseMetadata {

  val headers: EndpointOutput[PagingResponseMetadata] =
    (
      header[Int]("X-Page") and
        header[Int]("X-Elements-Per-Page") and
        header[Int]("X-Total-Pages") and
        header[Option[Int]]("X-Prev-Page") and
        header[Option[Int]]("X-Next-Page")
    ).mapTo[PagingResponseMetadata]

  def of[A](inputParams: Paging, paginationEnvelope: PaginationEnvelope[A]): PagingResponseMetadata = {
    val totalPages = (paginationEnvelope.totalResults / inputParams.limit.value.doubleValue).ceil.toInt

    PagingResponseMetadata(
      inputParams.page.value,
      inputParams.limit.value,
      totalPages,
      if (inputParams.page.value > 1 && inputParams.page.value - 1 <= totalPages) Some(inputParams.page.value - 1) else None,
      if (inputParams.page.value < totalPages) Some(inputParams.page.value + 1) else None
    )
  }

}
