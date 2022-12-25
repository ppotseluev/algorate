package com.github.ppotseluev.algorate.redis

import java.nio.ByteBuffer

trait RedisEncoder[T] {
  def encode(obj: T): ByteBuffer
}
