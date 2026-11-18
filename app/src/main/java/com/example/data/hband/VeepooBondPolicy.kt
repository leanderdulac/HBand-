package com.example.data.hband

/**
 * The Veepoo/Inuker stack (and Android itself) may call createBond while the
 * GATT connect is in flight. On some Samsung phones the bond never settles:
 * BONDING falls back to BOND_NONE in a tight loop and the SDK never delivers
 * connect/notify. After a few cycles we abort the hung attempt and back off.
 *
 * Bond constants match [android.bluetooth.BluetoothDevice]: NONE=10, BONDING=11, BONDED=12.
 */
object VeepooBondPolicy {
    const val BOND_NONE = 10
    const val BONDING = 11
    const val BONDED = 12
    const val MAX_NONE_CYCLES = 3

    enum class Action { IGNORE, TRACK_NONE, RESET, ABORT_CONNECT }

    data class Decision(val action: Action, val noneCycles: Int)

    fun onBondState(previous: Int, next: Int, noneCyclesSoFar: Int): Decision {
        if (next == BONDED) return Decision(Action.RESET, 0)
        if (previous == BONDING && next == BOND_NONE) {
            val cycles = noneCyclesSoFar + 1
            val action = if (cycles >= MAX_NONE_CYCLES) Action.ABORT_CONNECT else Action.TRACK_NONE
            return Decision(action, cycles)
        }
        return Decision(Action.IGNORE, noneCyclesSoFar)
    }
}
