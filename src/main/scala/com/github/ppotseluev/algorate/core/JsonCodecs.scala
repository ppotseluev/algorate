package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.Bar
import com.github.ppotseluev.algorate.model.Price
import com.google.protobuf.util.JsonFormat
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser.parse
import ru.tinkoff.piapi.contract.v1.Share
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object JsonCodecs {
  implicit val priceEncoder: Encoder[Price] =
    Encoder[String].contramap(_.toString)

  implicit val durationEncoder: Encoder[FiniteDuration] =
    Encoder[String].contramap(_.toString)

  implicit val durationDecoder: Decoder[FiniteDuration] =
    Decoder[String].map(Duration(_).asInstanceOf[FiniteDuration])

  implicit val barEncoder: Encoder[Bar] = deriveEncoder
  implicit val barDecoder: Decoder[Bar] = deriveDecoder

  implicit val shareEncoder: Encoder[Share] = Encoder.instance(s =>
    parse(JsonFormat.printer.print(s)).getOrElse(
      throw new IllegalArgumentException(s"Can't serialize $s to json")
    )
  )

  implicit val shareDecoder: Decoder[Share] = Decoder.instance(c => {
    Try {
      val builder = Share.newBuilder
      JsonFormat.parser.merge(c.value.noSpaces, builder)
      builder.build
    }.toEither.left.map(DecodingFailure.fromThrowable(_, c.history))
  })
}
