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

    companion object {
        const val DEFAULT_PACKAGES = "com.app99.driver,com.ubercab.driver"
    }
}
