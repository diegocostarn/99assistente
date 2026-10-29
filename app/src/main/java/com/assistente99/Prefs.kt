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

    companion object {
        const val DEFAULT_PACKAGES = "com.app99.driver,com.ubercab.driver"
    }
}
