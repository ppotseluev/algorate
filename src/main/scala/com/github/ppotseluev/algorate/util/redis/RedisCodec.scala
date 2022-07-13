package com.github.ppotseluev.algorate.util.redis

import dev.profunktor.redis4cats.data.{RedisCodec => DRedisCodec}
import io.lettuce.core.RedisException
import io.lettuce.core.codec.{RedisCodec => JRedisCodec}
import java.nio.ByteBuffer
import scala.language.implicitConversions
import scala.util.Try

trait RedisCodec[T] extends RedisEncoder[T] with RedisDecoder[T] {
  def iemap[B](f: T => Either[String, B])(g: B => T): RedisCodec[B] = {
    val codec = this
    new RedisCodec[B] {
      override def encode(obj: B): ByteBuffer =
        codec.encode(g(obj))

      override def decode(bytes: ByteBuffer): Either[String, B] =
        codec.decode(bytes).flatMap(f)
    }
  }
}

object RedisCodec {

  implicit def apply[T](implicit
      encoder: RedisEncoder[T],
      decoder: RedisDecoder[T]
  ): RedisCodec[T] = new RedisCodec[T] {
    override def encode(obj: T): ByteBuffer = encoder.encode(obj)

    override def decode(bytes: ByteBuffer): Either[String, T] = decoder.decode(bytes)
  }
}

case class RedisCodecs[K, V]()(implicit
    val keyCodec: RedisCodec[K],
    val valueCodec: RedisCodec[V]
) {
  def iemapKey[K1](f: K => Either[String, K1])(g: K1 => K): RedisCodecs[K1, V] = {
    implicit val k1Codec: RedisCodec[K1] = keyCodec.iemap(f)(g)
    RedisCodecs()
  }

  def iemapValue[V1](f: V => Either[String, V1])(g: V1 => V): RedisCodecs[K, V1] = {
    implicit val v1Codec: RedisCodec[V1] = valueCodec.iemap(f)(g)
    RedisCodecs()
  }
}

object RedisCodecs {
  implicit val string: RedisCodecs[String, String] =
    wrap(DRedisCodec.Utf8)

  implicit def apply[K, V](implicit redisCodecs: RedisCodecs[K, V]): RedisCodecs[K, V] =
    redisCodecs

  def wrap[K, V](redisCodec: DRedisCodec[K, V]): RedisCodecs[K, V] = {
    val codec = redisCodec.underlying
    RedisCodecs()(
      keyCodec = new RedisCodec[K] {
        override def encode(obj: K): ByteBuffer = codec.encodeKey(obj)

        override def decode(bytes: ByteBuffer): Either[String, K] =
          Try(codec.decodeKey(bytes)).toEither.left.map(_.getMessage)
      },
      valueCodec = new RedisCodec[V] {
        override def encode(obj: V): ByteBuffer = codec.encodeValue(obj)

        override def decode(bytes: ByteBuffer): Either[String, V] =
          Try(codec.decodeValue(bytes)).toEither.left.map(_.getMessage)
      }
    )
  }

  implicit def unwrap[K, V](redisCodecs: RedisCodecs[K, V]): DRedisCodec[K, V] = {
    def getOrThrow[T](result: Either[String, T]): T =
      result.left
        .map(new RedisException(_))
        .toTry
        .get

    val jCodec = new JRedisCodec[K, V] {
      override def decodeKey(bytes: ByteBuffer): K = getOrThrow {
        redisCodecs.keyCodec.decode(bytes)
      }

      override def decodeValue(bytes: ByteBuffer): V = getOrThrow {
        redisCodecs.valueCodec.decode(bytes)
      }

      override def encodeKey(key: K): ByteBuffer =
        redisCodecs.keyCodec.encode(key)

      override def encodeValue(value: V): ByteBuffer =
        redisCodecs.valueCodec.encode(value)
    }
    DRedisCodec(jCodec)
  }

}
