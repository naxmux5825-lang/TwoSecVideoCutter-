package com.example.twosecvideocutter

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.provider.MediaStore
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val picker = 41
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressLabel: TextView
    private lateinit var choose: Button
    private lateinit var start: Button
    private lateinit var pause: Button
    private lateinit var resume: Button
    private lateinit var stop: Button
    private lateinit var durationChoices: RadioGroup
    @Volatile private var paused = false
    @Volatile private var stopped = false
    @Volatile private var running = false
    private val pauseLock = Object()
    private var videoUri: Uri? = null
    private var baseName = "video"
    private var clipSeconds = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 28, 24, 28)
        }
        scroll.addView(root)
        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text; textSize = size; gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        root.addView(label("2 Sec Video Cutter", 27f))
        root.addView(label("MP4 / MKV ভিডিওকে ছোট ক্লিপে ভাগ করুন", 15f))
        choose = Button(this).apply {
            text = "ভিডিও নির্বাচন করুন"
            setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(i, picker)
            }
        }
        root.addView(choose, fullWidth())
        details = label("এখনো কোনো ভিডিও নির্বাচন করা হয়নি।")
        root.addView(details)
        root.addView(label("প্রতি ক্লিপের দৈর্ঘ্য নির্বাচন করুন", 17f))
        durationChoices = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL; gravity = Gravity.CENTER }
        listOf(2, 3, 5, 10, 15, 20, 30, 60).forEach { sec ->
            durationChoices.addView(RadioButton(this).apply {
                text = "${sec}s"; id = View.generateViewId(); isChecked = sec == 2
                setOnClickListener { clipSeconds = sec; updateVideoInfo() }
            })
        }
        root.addView(durationChoices)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        root.addView(progress, fullWidth())
        progressLabel = label("0%", 14f); root.addView(progressLabel)
        status = label("ভিডিও নির্বাচন করে কাটিং শুরু করুন।", 15f); root.addView(status)
        start = Button(this).apply { text = "ক্লিপ কাটা শুরু করুন"; isEnabled = false; setOnClickListener { beginCutting() } }
        pause = Button(this).apply { text = "Pause"; isEnabled = false; setOnClickListener { pauseCutting() } }
        resume = Button(this).apply { text = "Resume"; isEnabled = false; setOnClickListener { resumeCutting() } }
        stop = Button(this).apply { text = "Stop"; isEnabled = false; setOnClickListener { stopCutting() } }
        listOf(start, pause, resume, stop).forEach { root.addView(it, fullWidth()) }
        setContentView(scroll)
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    @Deprecated("Kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != picker || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        videoUri = uri
        baseName = displayName(uri).substringBeforeLast('.', "video").ifBlank { "video" }
        updateVideoInfo()
        start.isEnabled = true
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0) ?: "video"
        }
        return "video"
    }

    private fun durationUs(uri: Uri): Long {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(this, uri, null)
            (0 until ex.trackCount).mapNotNull { try { ex.getTrackFormat(it).getLong(MediaFormat.KEY_DURATION) } catch (_: Exception) { null } }.maxOrNull() ?: 0L
        } finally { ex.release() }
    }

    private fun updateVideoInfo() {
        val uri = videoUri ?: return
        val dur = durationUs(uri)
        if (dur <= 0) { status.text = "ভিডিওর দৈর্ঘ্য পড়া যায়নি।"; return }
        val count = ((dur + clipSeconds * 1_000_000L - 1) / (clipSeconds * 1_000_000L)).toInt()
        details.text = "ভিডিও: ${displayName(uri)}\nদৈর্ঘ্য: ${timeText(dur / 1_000_000.0)}\nক্লিপ হবে: $count টি"
        status.text = "প্রতি ক্লিপ $clipSeconds সেকেন্ড। আনুমানিক সময় প্রথম ক্লিপের পর হিসাব হবে।"
    }

    private fun beginCutting() {
        val uri = videoUri ?: return
        if (running) return
        paused = false; stopped = false; running = true
        setBusy(true); progress.visibility = View.VISIBLE; progress.progress = 0; progressLabel.text = "0%"
        status.text = "কাটিং শুরু হচ্ছে…"
        worker.execute {
            val startedAt = System.currentTimeMillis()
            var made = 0
            try {
                val dur = durationUs(uri)
                val segment = clipSeconds * 1_000_000L
                val total = ((dur + segment - 1) / segment).toInt()
                for (i in 0 until total) {
                    waitWhilePaused()
                    if (stopped) break
                    val from = i * segment
                    val to = minOf(from + segment, dur)
                    if (to > from) { exportClip(uri, from, to, i + 1); made++ }
                    val pct = (made * 100.0 / total).roundToInt()
                    val remain = if (made > 0) ((System.currentTimeMillis() - startedAt).toDouble() / made * (total - made) / 1000.0) else -1.0
                    runOnUiThread {
                        progress.progress = pct
                        progressLabel.text = "$pct%  •  $made / $total ক্লিপ"
                        status.text = "তৈরি: $made টি  |  বাকি: ${total - made} টি\n" + if (remain >= 0) "আনুমানিক বাকি সময়: ${timeText(remain)}" else "সময় হিসাব করা হচ্ছে…"
                    }
                }
                runOnUiThread {
                    status.text = if (stopped) "Stop করা হয়েছে। $made টি ক্লিপ সেভ হয়েছে।" else "সম্পন্ন! $made টি ক্লিপ Movies/2SecVideoCutter ফোল্ডারে সেভ হয়েছে।"
                    if (!stopped) { progress.progress = 100; progressLabel.text = "100%" }
                    running = false; setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "সমস্যা: ${e.message ?: "ভিডিও প্রসেস করা যায়নি"}"; running = false; setBusy(false) }
            }
        }
    }

    private fun waitWhilePaused() {
        synchronized(pauseLock) { while (paused && !stopped) pauseLock.wait() }
    }
    private fun pauseCutting() {
        if (!running) return
        paused = true; pause.isEnabled = false; resume.isEnabled = true
        status.text = "Pause করা হয়েছে। Resume চাপলে আবার চলবে।"
    }
    private fun resumeCutting() {
        if (!running) return
        synchronized(pauseLock) { paused = false; pauseLock.notifyAll() }
        pause.isEnabled = true; resume.isEnabled = false
    }
    private fun stopCutting() {
        if (!running) return
        stopped = true
        synchronized(pauseLock) { paused = false; pauseLock.notifyAll() }
        stop.isEnabled = false; status.text = "Stop করা হচ্ছে… চলমান ক্লিপ শেষ হলে থামবে।"
    }
    private fun setBusy(busy: Boolean) {
        choose.isEnabled = !busy; start.isEnabled = !busy && videoUri != null
        for (i in 0 until durationChoices.childCount) durationChoices.getChildAt(i).isEnabled = !busy
        pause.isEnabled = busy; resume.isEnabled = false; stop.isEnabled = busy
    }

    private fun exportClip(uri: Uri, fromUs: Long, toUs: Long, number: Int) {
        val extractor = MediaExtractor()
        extractor.setDataSource(this, uri, null)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${baseName}_${number.toString().padStart(3, '0')}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/2SecVideoCutter")
        }
        val outUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("আউটপুট ফাইল তৈরি করা যায়নি")
        val pfd = contentResolver.openFileDescriptor(outUri, "w") ?: error("আউটপুট ফাইল খোলা যায়নি")
        var muxer: MediaMuxer? = null
        try {
            muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val map = mutableMapOf<Int, Int>()
            for (track in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(track)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) { map[track] = muxer.addTrack(fmt); extractor.selectTrack(track) }
            }
            check(map.isNotEmpty()) { "ভিডিও/অডিও ট্র্যাক পাওয়া যায়নি" }
            muxer.start()
            val buffer = java.nio.ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val firstPts = mutableMapOf<Int, Long>()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val pts = extractor.sampleTime
                if (pts >= toUs) break
                if (map.containsKey(track) && pts >= fromUs - 1_000_000L) {
                    buffer.clear(); val size = extractor.readSampleData(buffer, 0); if (size < 0) break
                    val base = firstPts.getOrPut(track) { pts }
                    info.set(0, size, max(0L, pts - base), extractor.sampleFlags)
                    muxer.writeSampleData(map[track]!!, buffer, info)
                }
                if (!extractor.advance()) break
            }
            muxer.stop()
        } catch (e: Exception) {
            try { contentResolver.delete(outUri, null, null) } catch (_: Exception) {}
            throw e
        } finally { try { muxer?.release() } catch (_: Exception) {}; extractor.release(); pfd.close() }
    }

    private fun timeText(seconds: Double): String {
        val s = max(0, seconds.roundToInt()); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    override fun onDestroy() {
        synchronized(pauseLock) { stopped = true; paused = false; pauseLock.notifyAll() }
        worker.shutdown()
        super.onDestroy()
    }
}
