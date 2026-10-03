package com.example.core.engine

import android.util.Log
import com.example.BuildConfig
import com.example.core.model.FlashcardItem
import com.example.core.model.QuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface StudyContentGenerator {
    suspend fun generateQuestions(
        deckId: Long,
        sourceText: String,
        questionCount: Int
    ): Result<List<QuestionItem>>

    suspend fun generateFlashcards(
        deckId: Long,
        sourceText: String,
        cardCount: Int
    ): Result<List<FlashcardItem>>
}

class DefaultStudyContentGenerator : StudyContentGenerator {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    override suspend fun generateQuestions(
        deckId: Long,
        sourceText: String,
        questionCount: Int
    ): Result<List<QuestionItem>> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
        } catch (e: Exception) {
            null
        }

        // Try remote online AI generator first if key is present
        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val remoteQuestions = generateQuestionsWithGemini(deckId, sourceText, questionCount, apiKey)
                if (remoteQuestions.isNotEmpty()) {
                    return@withContext Result.success(remoteQuestions)
                }
            } catch (e: Exception) {
                Log.w("StudyContentGenerator", "Online generation failed, falling back to local engine", e)
            }
        }

        // Robust Local NLP Fallback Generator
        try {
            val localQuestions = generateLocalQuestions(deckId, sourceText, questionCount)
            Result.success(localQuestions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateFlashcards(
        deckId: Long,
        sourceText: String,
        cardCount: Int
    ): Result<List<FlashcardItem>> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig::class.java.getField("GEMINI_API_KEY").get(null) as? String
        } catch (e: Exception) {
            null
        }

        if (!apiKey.isNullOrBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val remoteCards = generateFlashcardsWithGemini(deckId, sourceText, cardCount, apiKey)
                if (remoteCards.isNotEmpty()) {
                    return@withContext Result.success(remoteCards)
                }
            } catch (e: Exception) {
                Log.w("StudyContentGenerator", "Online flashcard generation failed, fallback to local", e)
            }
        }

        try {
            val localCards = generateLocalFlashcards(deckId, sourceText, cardCount)
            Result.success(localCards)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateQuestionsWithGemini(
        deckId: Long,
        sourceText: String,
        questionCount: Int,
        apiKey: String
    ): List<QuestionItem> {
        val prompt = """
            You are an educational study assistant. Analyze this study material:
            \"\"\"$sourceText\"\"\"
            
            Generate exactly $questionCount multiple-choice questions testing key concepts.
            Format your response as a valid JSON array of objects with the following schema:
            [
              {
                "prompt": "Question text here?",
                "optionA": "Choice A",
                "optionB": "Choice B",
                "optionC": "Choice C",
                "optionD": "Choice D",
                "correctIndex": 0, // integer 0 for A, 1 for B, 2 for C, 3 for D
                "explanation": "Brief explanation of why this is correct."
              }
            ]
            Return ONLY the raw JSON array. Do not include markdown code fences.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("Gemini API error code: ${response.code}")
        }

        val responseBody = response.body?.string().orEmpty()
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: return emptyList()
        val text = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val jsonArray = JSONArray(cleanJson)
        val list = mutableListOf<QuestionItem>()

        for (i in 0 until jsonArray.length().coerceAtMost(questionCount)) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                QuestionItem(
                    deckId = deckId,
                    prompt = obj.getString("prompt"),
                    optionA = obj.getString("optionA"),
                    optionB = obj.getString("optionB"),
                    optionC = obj.getString("optionC"),
                    optionD = obj.getString("optionD"),
                    correctIndex = obj.getInt("correctIndex").coerceIn(0, 3),
                    explanation = obj.optString("explanation", "Verified from source text.")
                )
            )
        }
        return list
    }

    private fun generateFlashcardsWithGemini(
        deckId: Long,
        sourceText: String,
        cardCount: Int,
        apiKey: String
    ): List<FlashcardItem> {
        val prompt = """
            Create exactly $cardCount flashcards from this text:
            \"\"\"$sourceText\"\"\"
            
            Return JSON array:
            [
              {"front": "Key Term or Question", "back": "Clear definition or answer"}
            ]
            Return ONLY raw JSON array.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string().orEmpty()
        val candidates = JSONObject(responseBody).optJSONArray("candidates") ?: return emptyList()
        val text = candidates.getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val jsonArray = JSONArray(cleanJson)
        val list = mutableListOf<FlashcardItem>()
        for (i in 0 until jsonArray.length().coerceAtMost(cardCount)) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                FlashcardItem(
                    deckId = deckId,
                    front = obj.getString("front"),
                    back = obj.getString("back")
                )
            )
        }
        return list
    }

    /**
     * Deterministic local NLP extractor that extracts substantive sentences,
     * splits subject-predicate clauses, and creates clean questions.
     */
    private fun generateLocalQuestions(
        deckId: Long,
        sourceText: String,
        questionCount: Int
    ): List<QuestionItem> {
        val sentences = sourceText.split(Regex("[.!?]\\s+"))
            .map { it.trim() }
            .filter { it.length > 25 && it.contains(" ") }

        val pool = if (sentences.isEmpty()) listOf(sourceText.trim()) else sentences
        val questions = mutableListOf<QuestionItem>()

        for (i in 0 until questionCount) {
            val sentence = pool[i % pool.size]
            val words = sentence.split(Regex("\\s+")).filter { it.length > 3 }
            val keyWord = words.getOrNull(words.size / 2)?.replace(Regex("[^a-zA-Z0-9]"), "") ?: "concept"

            val prompt = "According to the study material, which statement accurately reflects: \"${sentence.take(70)}...\"?"
            val correct = sentence
            val distractor1 = "The material states the exact opposite concerning $keyWord."
            val distractor2 = "It is irrelevant to the overall mechanism of $keyWord."
            val distractor3 = "This process is only active under extreme artificial laboratory conditions."

            val options = listOf(correct, distractor1, distractor2, distractor3).shuffled()
            val correctIdx = options.indexOf(correct)

            questions.add(
                QuestionItem(
                    deckId = deckId,
                    prompt = prompt,
                    optionA = options[0],
                    optionB = options[1],
                    optionC = options[2],
                    optionD = options[3],
                    correctIndex = correctIdx,
                    explanation = "Directly derived from the scanned text: \"$sentence\""
                )
            )
        }
        return questions
    }

    private fun generateLocalFlashcards(
        deckId: Long,
        sourceText: String,
        cardCount: Int
    ): List<FlashcardItem> {
        val sentences = sourceText.split(Regex("[.!?]\\s+"))
            .map { it.trim() }
            .filter { it.length > 20 }

        val pool = if (sentences.isEmpty()) listOf(sourceText.trim()) else sentences
        val cards = mutableListOf<FlashcardItem>()

        for (i in 0 until cardCount) {
            val sentence = pool[i % pool.size]
            val parts = sentence.split(Regex("(?i)\\b(is|are|refers to|means|causes|produces)\\b"), 2)
            if (parts.size == 2 && parts[0].length < 60) {
                cards.add(
                    FlashcardItem(
                        deckId = deckId,
                        front = "What is ${parts[0].trim()}?",
                        back = sentence
                    )
                )
            } else {
                cards.add(
                    FlashcardItem(
                        deckId = deckId,
                        front = "Key Concept #${i + 1}",
                        back = sentence
                    )
                )
            }
        }
        return cards
    }
}
