package com.github.huronapp.api.utils

import io.circe.DecodingFailure
import io.circe.literal._
import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OptionalValueSpec extends AnyWordSpec with Matchers {
  "json codec" should {
    "decode json" which {
      "has valid value" in {
        val inputJson = json"""{"value": "XYZ"}"""
        val result = inputJson.as[OptionalValue[String]]
        result shouldBe Right(OptionalValue(Some("XYZ")))
      }

      "has missing value" in {
        val inputJson = json"""{}"""
        val result = inputJson.as[OptionalValue[String]]
        result shouldBe Right(OptionalValue(None))
      }

      "has value set to null" in {
        val inputJson = json"""{"value": null}"""
        val result = inputJson.as[OptionalValue[String]]
        result shouldBe Right(OptionalValue(None))
      }
    }

    "not decode json" which {
      "has extra properties" in {
        val inputJson = json"""{"value": "XYZ", "foo": "bar"}"""
        val result = inputJson.as[OptionalValue[String]]
        result shouldBe Left(DecodingFailure("Unexpected field: [foo]; valid fields: value", List.empty))
      }
    }

    "encode json" which {
      "has value" in {
        val input = OptionalValue(Some("XYZ"))
        val result = input.asJson
        result shouldBe json"""{"value": "XYZ"}"""
      }
      "has no value" in {
        val input = OptionalValue[String](None)
        val result = input.asJson
        result shouldBe json"""{"value": null}"""
      }
    }
  }
}
