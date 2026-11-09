package com.framex.app.metrics

/** Cumulative jiffies from one `cpu` line of /proc/stat. */
internal data class CpuTimes(val total: Long, val idle: Long)
