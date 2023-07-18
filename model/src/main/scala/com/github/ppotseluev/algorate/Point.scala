package com.github.ppotseluev.algorate

import java.time.OffsetDateTime

case class Point(
    index: Int,
    timestamp: OffsetDateTime,
    value: Price
)
