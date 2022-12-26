package com.github.ppotseluev.algorate.trader

import org.ta4j.core.Position

import java.time.ZonedDateTime

case class EnrichedPosition(
    position: Position,
    entryTime: ZonedDateTime
)
