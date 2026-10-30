package com.assistente99

import android.content.Context

/** Configurações do usuário, guardadas localmente no aparelho. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("assistente99", Context.MODE_PRIVATE)

    /** Apps monitorados: 99 Motorista e Uber Motorista. */
    val packages: Set<String>
        get() = buildSet {
            if (enable99) add("com.app99.driver")
            if (enableUber) add("com.ubercab.driver")
        }

    // ---------- "Essa é boa" ----------

    var goodEnabled: Boolean
        get() = sp.getBoolean("good_enabled", true)
        set(v) = sp.edit().putBoolean("good_enabled", v).apply()

    /** R$/km mínimo para a corrida ser "boa" (padrão 2,00). */
    var goodMinPerKm: Float
        get() = sp.getFloat("good_min_per_km", 2.0f)
        set(v) = sp.edit().putFloat("good_min_per_km", v).apply()

    /** Distância máxima até o passageiro, em km, para a corrida ser "boa" (padrão 1,5). */
    var goodMaxPickupKm: Float
        get() = sp.getFloat("good_max_pickup_km", 1.5f)
        set(v) = sp.edit().putFloat("good_max_pickup_km", v).apply()

    var goodSound: Boolean
        get() = sp.getBoolean("good_sound", true)
        set(v) = sp.edit().putBoolean("good_sound", v).apply()

    /** Aceita sozinho as corridas boas (desligado por padrão). */
    var autoAccept: Boolean
        get() = sp.getBoolean("auto_accept", false)
        set(v) = sp.edit().putBoolean("auto_accept", v).apply()

    /** Segundos de espera antes de aceitar sozinho (1 a 10, padrão 2). */
    var autoAcceptSeconds: Int
        get() = sp.getInt("auto_accept_seconds", 2).coerceIn(1, 10)
        set(v) = sp.edit().putInt("auto_accept_seconds", v.coerceIn(1, 10)).apply()

    /** Diagnóstico: resultado do último aceite automático. */
    var lastAcceptLog: String
        get() = sp.getString("last_accept_log", "") ?: ""
        set(v) = sp.edit().putString("last_accept_log", v).apply()

    /** Tema do app: 0 automático (do celular), 1 claro, 2 escuro. */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    /** Segundos que o card fica na tela depois que a solicitação some (3 a 12). */
    var cardSeconds: Int
        get() = sp.getInt("card_seconds", 6).coerceIn(3, 12)
        set(v) = sp.edit().putInt("card_seconds", v.coerceIn(3, 12)).apply()

    /** Tema do card sobre os apps: 0 segue o tema do app, 1 claro, 2 escuro. */
    var cardTheme: Int
        get() = sp.getInt("card_theme", 0)
        set(v) = sp.edit().putInt("card_theme", v).apply()

    /** Chaves do botão flutuante: ligar/desligar a leitura de cada app. */
    var enableUber: Boolean
        get() = sp.getBoolean("enable_uber", true)
        set(v) = sp.edit().putBoolean("enable_uber", v).apply()

    var enable99: Boolean
        get() = sp.getBoolean("enable_99", true)
        set(v) = sp.edit().putBoolean("enable_99", v).apply()

    /** Botão flutuante sobre os outros apps. */
    var floatingButton: Boolean
        get() = sp.getBoolean("floating_button", true)
        set(v) = sp.edit().putBoolean("floating_button", v).apply()

    /** Lado da tela onde o botão flutuante fica preso: 0 esquerda, 1 direita. */
    var floatSide: Int
        get() = sp.getInt("float_side", 1)
        set(v) = sp.edit().putInt("float_side", v).apply()

    var floatY: Int
        get() = sp.getInt("float_y", -1)
        set(v) = sp.edit().putInt("float_y", v).apply()

    /** Abaixo disso o card fica vermelho. */
    var minPerKm: Float
        get() = sp.getFloat("min_per_km", 1.5f)
        set(v) = sp.edit().putFloat("min_per_km", v).apply()

    /** Acima disso o card fica verde. */
    var goodPerKm: Float
        get() = sp.getFloat("good_per_km", 2.0f)
        set(v) = sp.edit().putFloat("good_per_km", v).apply()

    /** Se true, o km até o passageiro entra no cálculo. */
    var includePickup: Boolean
        get() = sp.getBoolean("include_pickup", true)
        set(v) = sp.edit().putBoolean("include_pickup", v).apply()

    /** Modo diagnóstico: mostra um card mesmo quando nenhuma corrida é reconhecida. */
    var debugMode: Boolean
        get() = sp.getBoolean("debug_mode", false)
        set(v) = sp.edit().putBoolean("debug_mode", v).apply()

    /** Último conjunto de textos lidos da tela dos apps monitorados (para diagnóstico). */
    var lastDump: String
        get() = sp.getString("last_dump", "") ?: ""
        set(v) = sp.edit().putString("last_dump", v).apply()

    var lastDumpAt: Long
        get() = sp.getLong("last_dump_at", 0L)
        set(v) = sp.edit().putLong("last_dump_at", v).apply()

    /** Diagnóstico: apps que enviaram eventos de tela ao serviço, com contagem. */
    var seenPackages: String
        get() = sp.getString("seen_packages", "") ?: ""
        set(v) = sp.edit().putString("seen_packages", v).apply()

    /** Diagnóstico: resultado da última tentativa de ler a janela dos apps monitorados. */
    var lastStatus: String
        get() = sp.getString("last_status", "") ?: ""
        set(v) = sp.edit().putString("last_status", v).apply()

    /** Diagnóstico: última tela lida que continha preço (R$) e distância (km), mesmo depois de sumir. */
    var lastRideDump: String
        get() = sp.getString("last_ride_dump", "") ?: ""
        set(v) = sp.edit().putString("last_ride_dump", v).apply()

    var lastRideDumpAt: Long
        get() = sp.getLong("last_ride_dump_at", 0L)
        set(v) = sp.edit().putLong("last_ride_dump_at", v).apply()

    /** Diagnóstico: última leitura feita por OCR (captura de tela). */
    var lastOcrDump: String
        get() = sp.getString("last_ocr_dump", "") ?: ""
        set(v) = sp.edit().putString("last_ocr_dump", v).apply()

    /** Diagnóstico: últimas leituras de OCR que continham km (não são apagadas pela tela inicial lida depois). */
    var ocrRideHistory: String
        get() = sp.getString("ocr_ride_history", "") ?: ""
        set(v) = sp.edit().putString("ocr_ride_history", v).apply()

    /** Diagnóstico: leituras de OCR descartadas por não baterem com a estimativa da Uber. */
    var ocrRejected: String
        get() = sp.getString("ocr_rejected", "") ?: ""
        set(v) = sp.edit().putString("ocr_rejected", v).apply()

    // ---------- Aparência do card ----------

    /** Tamanho do card e dos textos, em %, de 60 a 200. */
    var cardScalePercent: Int
        get() = sp.getInt("card_scale", 100).coerceIn(60, 200)
        set(v) = sp.edit().putInt("card_scale", v.coerceIn(60, 200)).apply()

    val cardScale: Float get() = cardScalePercent / 100f

    /** Opacidade do card (fundo e borda), em %, de 10 a 100. */
    var cardAlpha: Int
        get() = sp.getInt("card_alpha", 90).coerceIn(10, 100)
        set(v) = sp.edit().putInt("card_alpha", v.coerceIn(10, 100)).apply()

    /** Posição na tela, de 0 a 8 (ver [RideCardView.POSITION_NAMES]). */
    var cardPosition: Int
        get() = sp.getInt("card_position", 0).coerceIn(0, 8)
        set(v) = sp.edit().putInt("card_position", v.coerceIn(0, 8)).apply()

    /** Posição livre do card (pixels, canto superior esquerdo). -1 = usar a posição fixa de [cardPosition]. */
    var cardX: Int
        get() = sp.getInt("card_x", -1)
        set(v) = sp.edit().putInt("card_x", v).apply()

    var cardY: Int
        get() = sp.getInt("card_y", -1)
        set(v) = sp.edit().putInt("card_y", v).apply()

    var showPerKm: Boolean
        get() = sp.getBoolean("show_per_km", true)
        set(v) = sp.edit().putBoolean("show_per_km", v).apply()

    var showPerHour: Boolean
        get() = sp.getBoolean("show_per_hour", true)
        set(v) = sp.edit().putBoolean("show_per_hour", v).apply()

    var showPrice: Boolean
        get() = sp.getBoolean("show_price", true)
        set(v) = sp.edit().putBoolean("show_price", v).apply()

    var showKm: Boolean
        get() = sp.getBoolean("show_km", true)
        set(v) = sp.edit().putBoolean("show_km", v).apply()

    var showRating: Boolean
        get() = sp.getBoolean("show_rating", true)
        set(v) = sp.edit().putBoolean("show_rating", v).apply()

    var showRides: Boolean
        get() = sp.getBoolean("show_rides", true)
        set(v) = sp.edit().putBoolean("show_rides", v).apply()

    var showTime: Boolean
        get() = sp.getBoolean("show_time", false)
        set(v) = sp.edit().putBoolean("show_time", v).apply()

    /** Volta a aparência do card ao padrão (não mexe nas faixas de R$/km nem no custo do carro). */
    fun resetCard() {
        sp.edit()
            .remove("card_seconds").remove("card_theme").remove("card_x").remove("card_y").remove("card_scale").remove("card_alpha").remove("card_position")
            .remove("show_per_km").remove("show_per_hour").remove("show_price")
            .remove("show_km").remove("show_time").remove("show_rating").remove("show_rides")
            .apply()
    }

    /**
     * Ler por imagem (OCR) também quando a tela do app tem algum texto, mas a corrida não foi reconhecida
     * (a 99 às vezes expõe só parte do card). Gasta mais bateria: uma leitura a cada ~2 s com o app na frente.
     */
    var ocrWhenText: Boolean
        get() = sp.getBoolean("ocr_when_text", true)
        set(v) = sp.edit().putBoolean("ocr_when_text", v).apply()

    /** Pede a captura de tela sozinho ao abrir o app, se ela não estiver ligada. */
    var autoCapture: Boolean
        get() = sp.getBoolean("auto_capture", true)
        set(v) = sp.edit().putBoolean("auto_capture", v).apply()

    /** Notificação silenciosa com a última corrida e botões de mapa. */
    var notifyLastRide: Boolean
        get() = sp.getBoolean("notify_last_ride", true)
        set(v) = sp.edit().putBoolean("notify_last_ride", v).apply()

    companion object {
        const val DEFAULT_PACKAGES = "com.app99.driver,com.ubercab.driver"
    }
}
