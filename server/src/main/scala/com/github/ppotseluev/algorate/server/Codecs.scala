package com.github.ppotseluev.algorate.server

import boopickle.Default._
import com.github.ppotseluev.algorate.Bar
import com.github.ppotseluev.algorate.Price
import com.google.protobuf.util.JsonFormat
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.parser.parse
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import ru.tinkoff.piapi.contract.v1.Share
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Codecs {
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

  implicit val barPickler: Pickler[Bar] = {
    case class View(
        openPrice: BigDecimal,
        closePrice: BigDecimal,
        lowPrice: BigDecimal,
        highPrice: BigDecimal,
        volume: Double,
        endTimeSeconds: Long,
        endTimeNano: Int,
        duration: FiniteDuration
    ) {
      def toBar: Bar = Bar(
        openPrice = openPrice,
        closePrice = closePrice,
        lowPrice = lowPrice,
        highPrice = highPrice,
        volume = volume,
        endTime = OffsetDateTime.ofInstant(
          Instant.ofEpochSecond(endTimeSeconds).plusNanos(endTimeNano),
          ZoneOffset.UTC
        ),
        duration = duration
      )
    }

    def toView(bar: Bar): View =
      View(
        openPrice = bar.openPrice,
        closePrice = bar.closePrice,
        lowPrice = bar.lowPrice,
        highPrice = bar.highPrice,
        volume = bar.volume,
        endTimeSeconds = bar.endTime.toEpochSecond,
        endTimeNano = bar.endTime.getNano,
        duration = bar.duration
      )

    new Pickler[Bar] {
      val _pickler = implicitly[Pickler[View]]

      override def pickle(obj: Bar)(implicit state: PickleState): Unit =
        _pickler.pickle(toView(obj))

      override def unpickle(implicit state: UnpickleState): Bar =
        _pickler.unpickle.toBar
    }
  }

}
