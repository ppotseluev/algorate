package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.Price
import com.github.ppotseluev.algorate.model.Tags
import com.softwaremill.tagging.Tagger
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto._
import java.time.OffsetDateTime
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

case class Bar(
    openPrice: Price,
    closePrice: Price,
    lowPrice: Price,
    highPrice: Price,
    volume: Long,
    endTime: OffsetDateTime,
    duration: FiniteDuration
)

object Bar {
  implicit val priceEncoder: Encoder[Price] =
    Encoder[String].contramap(_.toString)

  implicit val priceDecoder: Decoder[Price] =
    Decoder[String].map(BigDecimal(_).taggedWith[Tags.Price])

  implicit val durationEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)

  implicit val durationDecoder: Decoder[FiniteDuration] =
    Decoder[String].map(Duration(_).asInstanceOf[FiniteDuration])

  implicit val encoder: Encoder[Bar] = deriveEncoder
  implicit val decoder: Decoder[Bar] = deriveDecoder
}
