package com.github.ppotseluev.algorate.redis

import java.nio.ByteBuffer

trait RedisDecoder[T] {
  def decode(bytes: ByteBuffer): Either[String, T]
}
