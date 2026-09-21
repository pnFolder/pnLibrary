package ru.privatenull.pnlibrary.demo

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DemoState(val joins: AtomicLong) {
    val balances: MutableMap<UUID, BigDecimal> = ConcurrentHashMap()
}
