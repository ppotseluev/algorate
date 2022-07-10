package com.github.ppotseluev.algorate.util.redis

import io.circe._
import io.circe.syntax._
import io.circe.parser.decode

object RedisJsonCodec {

  implicit class Syntax[K](codecs: RedisCodecs[K, String]) {
    def jsonValues[V](implicit encoder: Encoder[V], decoder: Decoder[V]): RedisCodecs[K, V] =
      codecs.imapValue(decode(_).left.map(_.getMessage))(
        _.asJson.noSpaces
      )
  }
}
