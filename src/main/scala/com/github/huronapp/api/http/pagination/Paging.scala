package com.github.huronapp.api.http.pagination

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import sttp.tapir._
import sttp.tapir.codec.refined._
import sttp.tapir.ztapir.query
import eu.timepit.refined.auto._

final case class Paging(page: Refined[Int, Positive], limit: Refined[Int, Positive]) {

  val dropCount: Int = (page - 1) * limit

}

object Paging {

  private def positiveIntMaxValidator(max: Refined[Int, Positive]): Validator[Refined[Int, Positive]] =
    Validator.max(max.value).contramap(_.value)

  def params(
    defaultPage: Refined[Int, Positive] = 1,
    defaultLimit: Refined[Int, Positive] = 30,
    maxLimit: Refined[Int, Positive] = 100
  ): EndpointInput[Paging] =
    (
      query[Refined[Int, Positive]]("page").description("Start with specified page").default(defaultPage) and
        query[Refined[Int, Positive]]("limit")
          .description("Max number of results per page")
          .default(defaultLimit)
          .validate(positiveIntMaxValidator(maxLimit))
    ).mapTo[Paging]

}
