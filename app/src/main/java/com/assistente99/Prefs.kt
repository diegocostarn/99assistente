package com.assistente99

import android.content.Context

/** Configurações do usuário, guardadas localmente no aparelho. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("assistente99", Context.MODE_PRIVATE)

    /** Pacotes dos apps monitorados, separados por vírgula (99 Motorista e Uber Motorista por padrão). */
    var packagesText: String
        get() = sp.getString("pkgs", DEFAULT_PACKAGES) ?: DEFAULT_PACKAGES
        set(v) = sp.edit().putString("pkgs", v.trim()).apply()

    val packages: Set<String>
        get() = packagesText.split(',', ';', ' ', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /** Abaixo disso o card fica vermelho. */
    var minPerKm: Float
        get() = sp.getFloat("min_per_km", 1.5f)
        set(v) = sp.edit().putFloat("min_per_km", v).apply()

    /** Acima disso o card fica verde. */
    var goodPerKm: Float
        get() = sp.getFloat("good_per_km", 2.0f)
        set(v) = sp.edit().putFloat("good_per_km", v).apply()

    /** Custo do veículo por km (combustível, desgaste). Usado para estimar o lucro. */
    var costPerKm: Float
        get() = sp.getFloat("cost_per_km", 0.0f)
        set(v) = sp.edit().putFloat("cost_per_km", v).apply()

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

    /** Opacidade do card, em %, de 30 a 100. */
    var cardAlpha: Int
        get() = sp.getInt("card_alpha", 90).coerceIn(30, 100)
        set(v) = sp.edit().putInt("card_alpha", v.coerceIn(30, 100)).apply()

    /** Fonte: 0 padrão, 1 condensada, 2 serifada, 3 monoespaçada, 4 casual. */
    var cardFont: Int
        get() = sp.getInt("card_font", 0)
        set(v) = sp.edit().putInt("card_font", v).apply()

    /** Posição na tela, de 0 a 8 (ver [RideCardView.POSITION_NAMES]). */
    var cardPosition: Int
        get() = sp.getInt("card_position", 0).coerceIn(0, 8)
        set(v) = sp.edit().putInt("card_position", v.coerceIn(0, 8)).apply()

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

    var showTime: Boolean
        get() = sp.getBoolean("show_time", false)
        set(v) = sp.edit().putBoolean("show_time", v).apply()

    var showProfit: Boolean
        get() = sp.getBoolean("show_profit", true)
        set(v) = sp.edit().putBoolean("show_profit", v).apply()

    /** Volta a aparência do card ao padrão (não mexe nas faixas de R$/km nem no custo do carro). */
    fun resetCard() {
        sp.edit()
            .remove("card_scale").remove("card_alpha").remove("card_font").remove("card_position")
            .remove("show_per_km").remove("show_per_hour").remove("show_price")
            .remove("show_km").remove("show_time").remove("show_profit")
            .apply()
    }

    companion object {
        const val DEFAULT_PACKAGES = "com.app99.driver,com.ubercab.driver"
    }
}
