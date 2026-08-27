package com.kadaikutty.pos.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
class MoneyTest { @Test fun `line total uses minor units exactly`() { assertEquals(Money(750), Money(250) * 3) } }
