package com.example.data.hband

/**
 * Política de bateria do VE30 (Android 1.8.0 / iOS build 19).
 *
 * Beta testers (iOS e Android) relataram o relógio esgotando a bateria com
 * leituras demais. Mesmo com a pausa de 5 min do 1.7.9, o app ainda ligava o
 * PPG por ~2 min a cada ~7 min (FC 60 s + SpO2 30 s + HRV 15 s + PA 40 s) e
 * a cada reconexão o ciclo começava "forçado", sem pausa. Além disso a
 * temperatura ficava em detecção contínua.
 *
 * A partir daqui:
 * - quem mede é o próprio relógio (medição automática do firmware): FC a cada
 *   [HR_AUTO_INTERVAL_MIN] min; PA no máximo a cada [BP_AUTO_INTERVAL_MIN] min
 *   (só se o firmware aceitar mudar o intervalo; senão fica desligada);
 *   SpO2/HRV/estresse/temperatura/glicose/composição: desligados (sob demanda);
 * - o app só lê o histórico gravado no relógio a cada [HISTORY_POLL_MS];
 * - teste óptico ao vivo (FC → SpO2 → PA) só quando o paciente toca em medir,
 *   com o app em primeiro plano, no máximo um a cada [MANUAL_MIN_GAP_MS].
 */
object WatchBatteryPolicy {
    const val HR_AUTO_INTERVAL_MIN = 30
    const val BP_AUTO_INTERVAL_MIN = 60
    const val HISTORY_POLL_MS: Long = 30 * 60_000L
    const val MANUAL_MIN_GAP_MS: Long = 60_000L
    /** RSSI só informativo no caminho GATT genérico (antes: 4 s). */
    const val RSSI_POLL_MS: Long = 5 * 60_000L
    /**
     * Leitura de bateria no GATT cru. 5 s mantinha o rádio acordado o dia inteiro
     * e coincidia com o intervalo em que o firmware manda um pacote de FC ao vivo.
     */
    const val GATT_KEEPALIVE_MS: Long = 5 * 60_000L
    /** O ciclo PPG nunca se reagenda sozinho. */
    const val AUTOMATIC_LIVE_LOOP: Boolean = false
    /** Temperatura não fica em detecção contínua; só no teste manual. */
    const val CONTINUOUS_TEMPERATURE: Boolean = false
    /** Depois de muitas falhas de reconexão, espera mais entre tentativas (antes: 60 s). */
    const val RECONNECT_LONG_CAP_MS: Long = 5 * 60_000L
    const val RECONNECT_LONG_AFTER_ATTEMPTS = 8

    /** Nomes de [com.veepoo.protocol.model.enums.EAutoMeasureType] que ficam desligados. */
    val AUTO_OFF_TYPES: Set<String> = setOf(
        "BLOOD_OXYGEN", "HRV", "STRESS", "BODY_TEMPERATURE",
        "BLOOD_GLUCOSE", "BLOOD_COMPOSITION", "LORENZ",
    )

    /**
     * Campos de `CustomSetting` (EFunctionStatus) que um app antigo pode ter
     * deixado SUPPORT_OPEN. Ficam gravados no relógio. FC e PA automáticas não
     * entram: o intervalo delas é o protocolo de medição automática (30/60 min).
     * Fechar PPG também desliga o sono preciso nesse firmware.
     */
    val CONTINUOUS_CUSTOM_SWITCHES: Set<String> = setOf(
        "HRV", "PPG", "BODY_TEMPERATURE", "ECG_ALWAYS",
        "BLOOD_GLUCOSE", "STRESS", "BLOOD_COMPONENT",
    )

    const val OPCODE_REALTIME_HR: Byte = 0x04
    const val OPCODE_REALTIME_PPG: Byte = 0x15.toByte()
    const val OPCODE_REALTIME_SPO2: Byte = 0x20
    const val OPCODE_REALTIME_BP: Byte = 0x05

    /** Segundo byte 0x01 liga o teste; 0x00 desliga. Na conexão só sai o desliga. */
    fun realtimeCommand(opcode: Byte, start: Boolean): ByteArray =
        byteArrayOf(opcode, if (start) 0x01 else 0x00)

    fun connectStopCommands(): List<ByteArray> = listOf(
        realtimeCommand(OPCODE_REALTIME_PPG, start = false),
        realtimeCommand(OPCODE_REALTIME_SPO2, start = false),
        realtimeCommand(OPCODE_REALTIME_BP, start = false),
        // FC por último: se o rádio só guardar a última escrita, este é o fluxo de 5 s.
        realtimeCommand(OPCODE_REALTIME_HR, start = false),
    )

    fun acceptLiveSample(manualCycleActive: Boolean): Boolean = manualCycleActive

    fun customSwitchShouldClose(field: String, statusName: String?): Boolean =
        field in CONTINUOUS_CUSTOM_SWITCHES && statusName == "SUPPORT_OPEN"

    fun allDaySpo2ShouldClose(statusName: String?): Boolean = statusName == "SUPPORT_OPEN"

    data class AutoSlot(
        val type: String,
        val open: Boolean,
        val intervalMin: Int,
        val stepUnit: Int,
        val intervalModify: Boolean,
    )

    /**
     * Estado desejado de um item de medição automática, ou null se já está ok
     * (não escreve no relógio à toa). [heartRateUserOff]: o paciente desligou a
     * medição automática de FC em Ajustes — respeitar.
     */
    fun desired(slot: AutoSlot, heartRateUserOff: Boolean): AutoSlot? {
        val target = when (slot.type) {
            "PULSE_RATE" -> when {
                heartRateUserOff -> slot.copy(open = false)
                // Intervalo travado e mais curto que 30 min: desliga. Deixar ligado
                // mantém o sensor no ritmo que um app antigo gravou.
                !slot.intervalModify && slot.intervalMin < HR_AUTO_INTERVAL_MIN -> slot.copy(open = false)
                else -> slot.copy(open = true, intervalMin = intervalFor(slot, HR_AUTO_INTERVAL_MIN))
            }
            "BLOOD_PRESSURE" -> if (slot.intervalModify) {
                slot.copy(open = true, intervalMin = intervalFor(slot, BP_AUTO_INTERVAL_MIN))
            } else {
                slot.copy(open = false)
            }
            in AUTO_OFF_TYPES -> slot.copy(open = false)
            else -> slot
        }
        return if (target == slot) null else target
    }

    /** Arredonda para cima ao passo do firmware; sem permissão de mudar, mantém o atual. */
    fun intervalFor(slot: AutoSlot, wantedMin: Int): Int {
        if (!slot.intervalModify) return slot.intervalMin
        val step = slot.stepUnit.coerceAtLeast(1)
        val rounded = ((wantedMin + step - 1) / step) * step
        // Nunca encurtar um intervalo já mais longo que o desejado.
        return maxOf(rounded, slot.intervalMin)
    }

    fun manualAllowed(lastManualAtElapsedMs: Long?, nowElapsedMs: Long, foreground: Boolean): Boolean {
        if (!foreground) return false
        val last = lastManualAtElapsedMs ?: return true
        return nowElapsedMs - last >= MANUAL_MIN_GAP_MS
    }

    fun historyPollDue(lastPullAtElapsedMs: Long?, nowElapsedMs: Long): Boolean {
        val last = lastPullAtElapsedMs ?: return true
        return nowElapsedMs - last >= HISTORY_POLL_MS
    }

    fun reconnectDelayMs(baseDelayMs: Long, attempt: Int): Long =
        if (attempt >= RECONNECT_LONG_AFTER_ATTEMPTS) maxOf(baseDelayMs, RECONNECT_LONG_CAP_MS) else baseDelayMs
}
