package com.assistente99

/**
 * Estado compartilhado entre o serviço de acessibilidade e o serviço de captura de tela (OCR).
 *
 * O OCR só trabalha quando o serviço de acessibilidade vê uma tela "vazia" (poucos ou nenhum texto)
 * num app monitorado, que é o que acontece no card de solicitação da Uber. Assim ele não gasta
 * bateria nas telas que o serviço de acessibilidade já consegue ler.
 */
object OcrState {
    /** Serviço de captura ligado. */
    @Volatile var running = false

    /** O card na tela foi desenhado a partir do OCR. */
    @Volatile var shownByOcr = false

    /** Última vez que um app monitorado mostrou uma tela sem texto legível. */
    @Volatile var lastSparseAt = 0L

    /** Última vez que o serviço de acessibilidade reconheceu uma corrida sozinho. */
    @Volatile var lastAccessibilityRideAt = 0L

    /** Última vez que o OCR reconheceu uma corrida. */
    @Volatile var lastOcrRideAt = 0L

    const val GRACE_MS = 2500L
}
