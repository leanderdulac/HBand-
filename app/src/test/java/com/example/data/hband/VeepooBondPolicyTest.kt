package com.example.data.hband

import org.junit.Assert.assertEquals
import org.junit.Test

class VeepooBondPolicyTest {
    @Test
    fun bonding_to_none_is_tracked_until_the_abort_threshold() {
        var cycles = 0
        val first = VeepooBondPolicy.onBondState(VeepooBondPolicy.BONDING, VeepooBondPolicy.BOND_NONE, cycles)
        assertEquals(VeepooBondPolicy.Action.TRACK_NONE, first.action)
        cycles = first.noneCycles
        val second = VeepooBondPolicy.onBondState(VeepooBondPolicy.BONDING, VeepooBondPolicy.BOND_NONE, cycles)
        assertEquals(VeepooBondPolicy.Action.TRACK_NONE, second.action)
        val third = VeepooBondPolicy.onBondState(VeepooBondPolicy.BONDING, VeepooBondPolicy.BOND_NONE, second.noneCycles)
        assertEquals(VeepooBondPolicy.Action.ABORT_CONNECT, third.action)
        assertEquals(3, third.noneCycles)
    }

    @Test
    fun bonded_resets_the_none_counter() {
        val decision = VeepooBondPolicy.onBondState(VeepooBondPolicy.BONDING, VeepooBondPolicy.BONDED, 2)
        assertEquals(VeepooBondPolicy.Action.RESET, decision.action)
        assertEquals(0, decision.noneCycles)
    }

    @Test
    fun unrelated_transitions_are_ignored() {
        val decision = VeepooBondPolicy.onBondState(VeepooBondPolicy.BOND_NONE, VeepooBondPolicy.BONDING, 2)
        assertEquals(VeepooBondPolicy.Action.IGNORE, decision.action)
        assertEquals(2, decision.noneCycles)
    }
}
