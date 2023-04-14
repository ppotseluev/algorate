package com.github.ppotseluev.algorate

import java.time.ZonedDateTime
import org.ta4j.core.Position

case class EnrichedPosition(
    position: Position,
    entryTime: ZonedDateTime,
    asset: TradingAsset
)
