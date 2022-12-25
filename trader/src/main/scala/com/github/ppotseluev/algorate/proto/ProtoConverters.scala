package com.github.ppotseluev.algorate.proto

import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

object ProtoConverters {
  def fromProto(timestamp: Timestamp, zoneId: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(
      Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos),
      zoneId
    )
}
