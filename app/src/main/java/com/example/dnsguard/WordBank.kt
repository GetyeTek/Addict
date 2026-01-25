package com.guardian.net

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
        "sugardaddy", "sugarmummy", "pornhub", "xxxvideo",
        "habeshaxxx", "seksvideo", "erotika",

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

    fun getViolations(ctx: Context, text: String, exact: Boolean = false): List<String> {
        val violations = mutableListOf<String>()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val dynamicSet = prefs.getStringSet(KEY_DYNAMIC_WORDS, emptySet()) ?: emptySet()
        
        val allWords = (HARD_WORDS + dynamicSet)

        if (exact) {
            // TELEGRAM MODE: Use word boundaries to prevent 'i' or 'sex' inside 'essex' matching
            val lowerText = text.lowercase(Locale.ROOT)
            for (badWord in allWords) {
                val cleanBad = badWord.trim().lowercase(Locale.ROOT)
                if (cleanBad.length < 3) continue // GUARD: Ignore garbage 1-2 char words
                
                // Regex \b ensures we only match whole words
                val pattern = Regex("\\b" + Regex.escape(cleanBad) + "\\b")
                if (pattern.containsMatchIn(lowerText)) {
                    violations.add(cleanBad)
                }
            }
        } else {
            // BROWSER MODE: Use aggressive normalization (strips spaces) for obfuscation
            val normalizedContent = normalize(text)
            for (badWord in allWords) {
                val normalizedBad = normalize(badWord)
                if (normalizedBad.length < 3) continue
                if (normalizedContent.contains(normalizedBad)) {
                    violations.add(normalizedBad)
                }
            }
        }
        
        return violations
    }

    private fun normalize(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace("0", "o")
            .replace("1", "i")
            .replace("!", "i")
            .replace("@", "a")
            .replace("$", "s")
            .replace("3", "e")
            .filter { it.isLetter() || it.isDigit() } // Keep digits to support words like '18+' or '4k'
    }
}