package com.example.ankits

object TokenEstimator {
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var charScore = 0f
        for (c in text) {
            charScore += when {
                c.code in 0x4E00..0x9FFF -> 1.5f
                c.code in 0x3040..0x30FF -> 1.5f
                c.code > 0x7F -> 1.5f
                else -> 1f
            }
        }
        return (charScore / 4).toInt() + 1
    }

    fun estimate(msg: ChatMessage): Int {
        var total = estimate(msg.content)
        if (msg.imageBase64 != null) {
            total += 200
        }
        return total + 4
    }
}
