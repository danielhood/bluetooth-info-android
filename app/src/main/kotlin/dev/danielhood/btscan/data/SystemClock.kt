package dev.danielhood.btscan.data

import dev.danielhood.btscan.domain.port.Clock
import java.time.Instant

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
