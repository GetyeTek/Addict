package com.example.dnsguard

import android.content.Context
import java.util.Locale

object WordBank {

    private const val PREFS = "word_bank_prefs"
    private const val KEY_DYNAMIC_WORDS = "dynamic_words"

    // TIER 1: THE HARD LIST (Instant Block)
    // These words are rarely used in innocent contexts.
    private val HARD_WORDS = mutableSetOf(
        // English / Universal
        "porn", "xxx", "nude", "hentai", "sex",
        "onlyfans", "brazzers", "xhamster", "milf",
        "incest", "nsfw", "dick", "cock", "pussy",
        "whore", "slut", "18+", "adult", "erotic",
        "erotica", "fuck", "bitch", "blowjob",
        "sugardaddy", "sugarmummy",

        // Amharic (Latin / Transliteration)
        "wesib", "seks", "tidar", "agenagn", "sharmuta", 
        "shele", "ems", "qula", "bid", "beda", "tunda", 
        "boda", "habeshasex", "yebe", "jilba", "chik",
        
        // Amharic (Ge'ez Script)
        "ወሲብ", "ሴክስ", "ትዳር", "አገናኝ", "ሸርሙጣ", 
        "ሽሌ", "ብድ", "በዳ", "ቁላ", "እምስ", 
        "የበዳ", "ቱንዳ", "ቦዳ", "ኒውድ", "ቂም"
    )

    fun isSafe(ctx: Context, text: String): Boolean {
        // 1. Normalize the text (De-obfuscate)
        val cleaned = normalize(text)
        
        // 2. Load Dynamic Words (Learned from AI)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dynamicSet = prefs.getStringSet(KEY_DYNAMIC_WORDS, emptySet()) ?: emptySet()
        
        // 3. Check Hard List
        for (word in HARD_WORDS) {
            if (cleaned.contains(word)) return false
        }

        // 4. Check Dynamic List
        for (word in dynamicSet) {
            if (cleaned.contains(word)) return false
        }

        return true
    }

    fun addBadWord(ctx: Context, word: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_DYNAMIC_WORDS, mutableSetOf()) ?: mutableSetOf()
        val newSet = HashSet(current)
        newSet.add(normalize(word))
        prefs.edit().putStringSet(KEY_DYNAMIC_WORDS, newSet).apply()
    }

    private fun normalize(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace("0", "o")
            .replace("1", "i")
            .replace("!", "i")
            .replace("@", "a")
            .replace("$", "s")
            .replace("3", "e")
            .filter { it.isLetter() } // Strip spaces and symbols to merge words (e.g., "P.o.r.n" -> "porn")
    }
}