package com.kadaikutty.pos.core.printer.data

import kotlinx.coroutines.sync.Mutex

/** One local print transfer at a time, including diagnostic tickets. */
object PrinterJobLock { val mutex = Mutex() }
