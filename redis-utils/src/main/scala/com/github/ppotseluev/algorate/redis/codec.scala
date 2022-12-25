package com.github.ppotseluev.algorate.redis

import boopickle.Default._
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._
import java.nio.ByteBuffer

object codec {

  implicit class StringOps[K](val codecs: RedisCodecs[K, String]) extends AnyVal {
    def jsonValues[V](implicit encoder: Encoder[V], decoder: Decoder[V]): RedisCodecs[K, V] =
      codecs.iemapValue(decode(_).left.map(_.getMessage))(
        _.asJson.noSpaces
      )
  }

  implicit class BinaryValueOps[K](val codecs: RedisCodecs[K, ByteBuffer]) extends AnyVal {
    def boopickleValues[V: Pickler]: RedisCodecs[K, V] =
      codecs.iemapValue(Unpickle[V].tryFromBytes(_).toEither.left.map(_.getMessage))(
        Pickle.intoBytes
      )
  }

  implicit class BinaryKeyOps[V](val codecs: RedisCodecs[ByteBuffer, V]) extends AnyVal {
    def stringKeys: RedisCodecs[String, V] = {
      import codecs.valueCodec
      import RedisCodecs.string.keyCodec
      RedisCodecs[String, V]()
    }
  }
}
