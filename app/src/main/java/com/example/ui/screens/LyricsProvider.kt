package com.example.ui.screens

import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

object LyricsProvider {
    fun getLyricsForTrack(title: String, artist: String?, filePath: String): List<LyricLine> {
        // Try to load physical sidecar LRC file first
        val sidecar = loadSidecarLyrics(filePath)
        if (sidecar != null) return sidecar

        val lowercaseTitle = title.lowercase()
        val lowercaseArtist = (artist ?: "").lowercase()

        return when {
            lowercaseTitle.contains("أعطني") || lowercaseTitle.contains("fairuz") || lowercaseTitle.contains("ناي") || lowercaseArtist.contains("فيروز") -> {
                listOf(
                    LyricLine(0L, "موسيقى مقدمة أثيرية 🎶 (Fairuz Intro)"),
                    LyricLine(3000L, "أعطني الناي وغنّ 🎶"),
                    LyricLine(10000L, "فالغناء سرّ الوجود ✨"),
                    LyricLine(18000L, "وأنين الناي يبقى 🎷"),
                    LyricLine(25000L, "بعد أن يفنى الوجود 💫"),
                    LyricLine(33000L, "هل اتّخذت الغاب مثلي؟ 🌳"),
                    LyricLine(40000L, "منزلاً دون القصور؟ 🏰"),
                    LyricLine(48000L, "تتبّعت السواقي؟ 💧"),
                    LyricLine(55000L, "وتسلّقت الصخور؟ ⛰️"),
                    LyricLine(63000L, "هل تحمّمت بعطرٍ؟ 🌸"),
                    LyricLine(71000L, "وتنشّفت بنور؟ ☀️"),
                    LyricLine(79000L, "وشربت الفجر خمراً؟ 🌅"),
                    LyricLine(87000L, "في كؤوسٍ من أثير؟ 🍷"),
                    LyricLine(95000L, "أعطني الناي وغنّ.. فالغناء سر الوجود ⏮️"),
                    LyricLine(105000L, "وأنين الناي يبقى.. بعد أن يفنى الوجود 💎"),
                    LyricLine(115000L, "فاصل معزوفة ناي دافئة (Instrumental break)..."),
                    LyricLine(135000L, "هل جلست العصر مثلي؟ 🌇"),
                    LyricLine(143000L, "بين جفنات العنب؟ 🍇"),
                    LyricLine(151000L, "والعناقيد تدلّت.."),
                    LyricLine(158000L, "كثريات الذهب؟ ✨"),
                    LyricLine(166000L, "هل فرشت العشب ليلاً؟ 🌌"),
                    LyricLine(174000L, "وتلحّفت الفضاء؟ 🪐"),
                    LyricLine(182000L, "زاهداً في ما سيأتي.."),
                    LyricLine(190000L, "ناسياً ما قد مضى؟ 💫"),
                    LyricLine(198000L, "أعطني الناي وغنّ.. فالغناء سر الوجود"),
                    LyricLine(206000L, "وأنين الناي يبقى.. بعد أن يفنى الوجود 🎻"),
                    LyricLine(215000L, "خاتمة معزوفة الناي الأثيرية (End of Song Journey)")
                )
            }
            lowercaseTitle.contains("ألقاك") || lowercaseTitle.contains("kulthum") || lowercaseTitle.contains("غداً") || lowercaseArtist.contains("كلثوم") -> {
                listOf(
                    LyricLine(0L, "مقدمة أوركسترا كلاسيكية شرقية 🎻 (Umm Kulthum Solo)"),
                    LyricLine(5000L, "أغداً ألقاك؟ يا خوف فؤادي من غدِ! ❤️"),
                    LyricLine(14000L, "يا لَشوقي واحتراقي بانتظار الموعدِ! 🔥"),
                    LyricLine(23000L, "آه من غدٍ وأهواهُ.. وأخشى ما فيهِ! 🥺"),
                    LyricLine(32000L, "أنا غداً ألقاك.. كم أهواهُ يا فؤادي! ✨"),
                    LyricLine(41000L, "هذه الدنيا عيون.. أنت فيها أملي 👁️"),
                    LyricLine(50000L, "هذه الدنيا ربيع.. أنت فيه غزلي 🌸"),
                    LyricLine(59000L, "لو تساءلت فؤادي.. عن غدِ والتقينا؟"),
                    LyricLine(68000L, "هل لقى الحب سواه؟ هل لقى العمر بديل؟ 💎"),
                    LyricLine(77000L, "أنت عمر في فؤادي.. فجر يوم قد بدا 🌅"),
                    LyricLine(86000L, "أغداً ألقاك؟ يا شوق فؤادي لغدِ!")
                )
            }
            lowercaseTitle.contains("فنجان") || lowercaseTitle.contains("abdel halim") || lowercaseTitle.contains("حليم") || lowercaseArtist.contains("حليم") -> {
                listOf(
                    LyricLine(0L, "مقدمة عازفة أوتار العندليب الأسمر 🎻 (Abdel Halim Intro)"),
                    LyricLine(4000L, "جلست.. والخوف بعينيها ☕"),
                    LyricLine(12000L, "تتأمل فنجاني المقلوب 🕊️"),
                    LyricLine(20000L, "قالت: يا ولدي.. لا تحزن 🔮"),
                    LyricLine(28000L, "فالحبّ عليك هو المكتوب.. يا ولدي ✍️"),
                    LyricLine(37000L, "قد مات شهيداً.. من مات فداءً للمحبوب! ❤️"),
                    LyricLine(46000L, "فنجانك دنيا مرعبةٌ.."),
                    LyricLine(54000L, "وحياتك أسفارٌ وحروب ⚔️"),
                    LyricLine(62000L, "ستحب كثيراً يا ولدي.."),
                    LyricLine(70000L, "وتموت كثيراً يا ولدي ✨"),
                    LyricLine(78000L, "وستعشق كل نساء الأرض.."),
                    LyricLine(86000L, "وترجع كالملك المغلوب! 👑")
                )
            }
            else -> {
                // Generate friendly synchronized lyrics based on duration if unknown
                listOf(
                    LyricLine(0L, "مقدمة اللحن الموسيقي المستمر... 🎼"),
                    LyricLine(5000L, "نحن نستمع إلى: $title 🌟"),
                    LyricLine(15000L, "للفنان الرائع: ${artist ?: "مجهول الهوية"} 💕"),
                    LyricLine(30000L, "الكلمات المتزامنة نشطة وجاهزة تحت تصرفك..."),
                    LyricLine(45000L, "يمكنك وضع ملف .lrc بنفس الاسم بجوار الأغنية المحددة لرفع الكلمات المخصصة تلقائياً 🌟"),
                    LyricLine(60000L, "جاري الاستمتاع بأذكى خلفية أورورا متفاعلة للألوان... 🌈"),
                    LyricLine(80000L, "شغف الموسيقى ينعش الأجواء باستمرار... ✨"),
                    LyricLine(110000L, "مستمرون في الاستماع بتناغم تام.. 🎵"),
                    LyricLine(140000L, "يمكنك الضغط على أي سطر من كلمات الأغنية لتمرير وقت التشغيل إليها مباشرة! ⏮️"),
                    LyricLine(180000L, "نهاية المحاكاة اللحنية للملف الصوتي الحالي 🎉")
                )
            }
        }
    }

    private fun loadSidecarLyrics(filePath: String): List<LyricLine>? {
        if (filePath.isEmpty() || filePath.startsWith("http")) return null
        try {
            val file = File(filePath)
            val parent = file.parentFile ?: return null
            val baseName = file.nameWithoutExtension
            // Try different capitalization of sidecar extension (.lrc, .LRC)
            var lrcFile = File(parent, "$baseName.lrc")
            if (!lrcFile.exists()) {
                lrcFile = File(parent, "$baseName.LRC")
            }
            if (lrcFile.exists()) {
                val lines = lrcFile.readLines()
                val parsed = mutableListOf<LyricLine>()
                // Matches [mm:ss] or [mm:ss.xx] or [mm:ss:xx] or [mm:ss.xxx]
                val regex = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]\\s*(.*)")
                for (line in lines) {
                    val matchResult = regex.find(line)
                    if (matchResult != null) {
                        val (min, sec, ms, text) = matchResult.destructured
                        val minutesMs = min.toLong() * 60 * 1000
                        val secondsMs = sec.toLong() * 1000
                        val millisecondPart = when {
                            ms.isEmpty() -> 0L
                            ms.length == 2 -> ms.toLong() * 10
                            else -> ms.toLong()
                        }
                        val totalMs = minutesMs + secondsMs + millisecondPart
                        parsed.add(LyricLine(totalMs, text.trim()))
                    }
                }
                if (parsed.isNotEmpty()) {
                    return parsed.sortedBy { it.timeMs }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
