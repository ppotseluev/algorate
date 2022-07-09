package com.github.ppotseluev.algorate.core

import com.github.ppotseluev.algorate.model.Price
import java.time.OffsetDateTime

case class Point(
    timestamp: OffsetDateTime,
    value: Price
)
