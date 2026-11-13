package com.drwho174.infusioncalc.domain.model

/** Пользовательская папка для группировки препаратов. */
data class Folder(
    val id: String,
    val name: String,
) {
    companion object {
        const val MAX_NAME_LENGTH = 40

        /** Имена, занятые служебным представлением «Все» (RU/EN). */
        val RESERVED_NAMES = setOf("все", "all")
    }
}
