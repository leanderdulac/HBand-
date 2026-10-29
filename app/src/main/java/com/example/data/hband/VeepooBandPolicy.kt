package com.example.data.hband

/**
 * A pulseira só enche o histórico do dia se a medição automática estiver
 * ligada nela. O app pede isso quando o usuário deixou o ajuste ativo e a
 * pulseira ainda está desligada. SpO2 noturno só muda quando a leitura
 * confirmou que está fechado.
 */
object VeepooBandPolicy {
    fun shouldEnableHeartAuto(userWants: Boolean, bandSwitchOpen: Boolean?): Boolean =
        userWants && bandSwitchOpen != true

    fun shouldEnableNightSpo2(userWants: Boolean, bandIsOpen: Boolean?): Boolean =
        userWants && bandIsOpen == false
}
