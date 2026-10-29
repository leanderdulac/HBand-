package com.example.data.hband

/** Describes this watch query, independently of the local history and server queue. */
fun historyReadStatusText(state: HistorySyncUiState): String {
    val received = buildList {
        if (state.originSamples > 0) add("${state.originSamples} registros gerais")
        if (state.sleepDays > 0) add("${state.sleepDays} dias com sono")
        if (state.hrvSamples > 0) add("${state.hrvSamples} registros de variação dos batimentos")
        if (state.spo2Samples > 0) add("${state.spo2Samples} registros de oxigênio")
    }.joinToString(", ")
    val partial = if (received.isNotEmpty()) " Recebidos nesta consulta: $received." else ""
    return when {
        state.isRunning -> "Recebendo o histórico do relógio…"
        state.phase == "cancelado" || state.lastError == "cancelado" ->
            "A leitura do histórico foi interrompida.$partial Os registros já salvos continuam no Histórico. Confira a conexão antes de tentar novamente."
        state.phase == "unavailable" ->
            "A leitura do histórico ainda não pôde começar: as opções do relógio não foram identificadas. Aguarde a conexão e tente novamente. Os registros já salvos continuam no Histórico."
        state.lastError != null || state.phase == "error" || state.phase == "incomplete" ->
            "Não foi possível concluir a leitura do histórico.$partial Isso não confirma ausência de dados no relógio. Os registros já salvos continuam no Histórico. Confira a conexão e tente novamente."
        state.lastCompletedAtMs == null ->
            "O aplicativo tenta receber o histórico após conectar o relógio. Você também pode tentar pelo botão abaixo."
        received.isEmpty() ->
            "O relógio não retornou registros nesta consulta. Os registros já salvos continuam no Histórico."
        else -> "Leitura do relógio concluída: $received. Consulte os registros salvos em Histórico."
    }
}
