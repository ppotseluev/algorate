package com.github.ppotseluev.algorate.util.redis

import io.circe._
import io.circe.parser.decode
import io.circe.syntax._

object RedisJsonCodec {

  implicit class Syntax[K](val codecs: RedisCodecs[K, String]) extends AnyVal {
    def jsonValues[V](implicit encoder: Encoder[V], decoder: Decoder[V]): RedisCodecs[K, V] =
      codecs.imapValue(decode(_).left.map(_.getMessage))(
        _.asJson.noSpaces
      )
  }
}
