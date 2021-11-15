package com.github.huronapp.api.http.pagination

import eu.timepit.refined.auto._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PagingResponseMetadataSpec extends AnyWordSpec with Matchers {
  "of method" should {
    "create instance" which {
      "has all values set" in {
        val inputParams = Paging(3, 5)
        val envelope = PaginationEnvelope(List("a", "b", "c", "d", "e"), 31)

        val result = PagingResponseMetadata.of(inputParams, envelope)

        result shouldBe PagingResponseMetadata(3, 5, 7, Some(2), Some(4))
      }

      "has no previous page set" when {
        "current page is 1" in {
          val inputParams = Paging(1, 5)
          val envelope = PaginationEnvelope(List("a", "b", "c", "d", "e"), 31)

          val result = PagingResponseMetadata.of(inputParams, envelope)

          result shouldBe PagingResponseMetadata(1, 5, 7, None, Some(2))
        }

        "current and previous pages are out of range" in {
          val inputParams = Paging(200, 5)
          val envelope = PaginationEnvelope(List(), 31)

          val result = PagingResponseMetadata.of(inputParams, envelope)

          result shouldBe PagingResponseMetadata(200, 5, 7, None, None)
        }
      }

      "has no next page set" when {
        "current page is the last one" in {
          val inputParams = Paging(7, 5)
          val envelope = PaginationEnvelope(List("a"), 31)

          val result = PagingResponseMetadata.of(inputParams, envelope)

          result shouldBe PagingResponseMetadata(7, 5, 7, Some(6), None)
        }

        "current page are out of range" in {
          val inputParams = Paging(8, 5)
          val envelope = PaginationEnvelope(List(), 31)

          val result = PagingResponseMetadata.of(inputParams, envelope)

          result shouldBe PagingResponseMetadata(8, 5, 7, Some(7), None)
        }
      }
    }
  }
}
