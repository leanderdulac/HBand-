package com.example.ui.components

import com.example.data.local.AiInsightConsentStore

/** Senior-friendly pt-BR copy for the Resumo com IA consent screen, card and Ajustes toggle. */
internal object AiInsightConsentCopy {
    const val TITLE = "Resumo com IA"
    const val ENABLE_BUTTON = "Ativar resumo com IA"
    const val AUTHORIZE = "Autorizo"
    const val NOT_NOW = "Agora não"
    const val DISABLED_BODY =
        "O resumo com IA explica suas medições em linguagem simples. Ele é opcional: o aplicativo funciona normalmente sem ele."
    const val DIALOG_INTRO =
        "O Resumo com IA é um texto simples sobre as suas medições dos últimos 7 dias."
    const val DATA_SENT = "Se você autorizar, o aplicativo envia para o Google Gemini:"
    val DATA_ITEMS = listOf(
        "seu nome",
        "sua idade",
        "seu sexo",
        "sua altura",
        "seu peso",
        "suas observações",
        "as médias dos sinais vitais dos últimos 7 dias",
    )
    const val PURPOSE =
        "O Google Gemini usa esses dados só para escrever o resumo em linguagem fácil."
    const val OPTIONAL =
        "Isso é opcional. O aplicativo continua funcionando normalmente sem o resumo."
    const val NOT_DIAGNOSIS =
        "O resumo não é um diagnóstico nem uma orientação médica."
    const val WITHDRAW =
        "Você pode desligar esta opção a qualquer momento em Ajustes."
    const val PRIVACY_LABEL = "Política de privacidade"
    const val DELETION_LABEL = "Como apagar seus dados"
    const val PRIVACY_URL = AiInsightConsentStore.PRIVACY_POLICY_URL
    const val DELETION_URL = AiInsightConsentStore.PRIVACY_DELETION_URL
    const val SETTINGS_OFF_HINT =
        "O aplicativo envia seus dados ao Google Gemini só se você autorizar."
    const val SETTINGS_ON_HINT =
        "Autorizado. Você pode desligar a qualquer momento."
}
