package com.example.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.local.InterviewSessionEntity
import com.example.data.local.SessionTurnEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates clean PDF and shareable plain-text interview reports using Android PdfDocument.
 */
object PdfReportExporter {

    fun formatAsPlainText(
        session: InterviewSessionEntity,
        turns: List<SessionTurnEntity>
    ): String = buildString {
        val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            .format(Date(session.createdAt))
        appendLine("PREPPILOT AI — INTERVIEW SESSION REPORT")
        appendLine("=======================================")
        appendLine("Date: $dateStr")
        appendLine("Role: ${session.role} (${session.seniority})")
        if (session.company.isNotBlank()) appendLine("Target Company: ${session.company}")
        appendLine("Type: ${session.interviewType} | Difficulty: ${session.difficulty} | Mode: ${session.mode}")
        appendLine("Overall Score: ${String.format(Locale.US, "%.1f", session.overallScore)} / 10")
        appendLine()

        if (session.topStrengths.isNotBlank()) {
            appendLine("TOP STRENGTHS")
            session.topStrengths.split("||").filter { it.isNotBlank() }.forEach {
                appendLine("  • $it")
            }
            appendLine()
        }
        if (session.topImprovements.isNotBlank()) {
            appendLine("AREAS TO IMPROVE")
            session.topImprovements.split("||").filter { it.isNotBlank() }.forEach {
                appendLine("  • $it")
            }
            appendLine()
        }
        if (session.nextPracticePlan.isNotBlank()) {
            appendLine("RECOMMENDED NEXT PRACTICE PLAN")
            appendLine(session.nextPracticePlan)
            appendLine()
        }

        appendLine("QUESTION-BY-QUESTION BREAKDOWN")
        appendLine("------------------------------")
        turns.forEachIndexed { idx, turn ->
            appendLine("Q${idx + 1} [${turn.topic}]: ${turn.question}")
            appendLine("Your Answer: ${turn.userAnswer.ifBlank { "(Skipped)" }}")
            appendLine(
                "Scores — Clarity: ${turn.clarityScore}/10 | Structure: ${turn.structureScore}/10 | " +
                    "Relevance: ${turn.relevanceScore}/10 | Depth: ${turn.depthScore}/10 | Confidence: ${turn.confidenceScore}/10"
            )
            if (turn.wpm > 0) {
                appendLine("Speech — ${turn.wpm} WPM | Fillers: ${turn.fillerCount} | Long Pauses: ${turn.longPauseCount}")
            }
            if (turn.idealAnswer.isNotBlank()) {
                appendLine("Ideal Answer: ${turn.idealAnswer}")
            }
            appendLine()
        }
    }

    fun writePdfToUri(
        context: Context,
        uri: Uri,
        session: InterviewSessionEntity,
        turns: List<SessionTurnEntity>
    ): Result<Unit> = runCatching {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = (pageWidth - margin * 2).toInt()

        val titlePaint = TextPaint().apply {
            color = Color.rgb(43, 68, 184)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headingPaint = TextPaint().apply {
            color = Color.rgb(30, 35, 64)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.rgb(55, 65, 81)
            textSize = 11f
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(220, 226, 255)
            strokeWidth = 1.5f
        }

        var pageNumber = 1
        var page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas
        var y = margin + 10f

        fun newPage() {
            pdfDocument.finishPage(page)
            pageNumber++
            page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = page.canvas
            y = margin
        }

        fun drawParagraph(text: String, paint: TextPaint, extraSpace: Float = 8f) {
            if (text.isBlank()) return
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.1f)
                .build()
            if (y + layout.height > pageHeight - margin) {
                newPage()
            }
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + extraSpace
        }

        drawParagraph("PrepPilot AI — Interview Report", titlePaint, 6f)
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(session.createdAt))
        val meta = "${session.role} (${session.seniority}) • ${session.interviewType} • $dateStr" +
            if (session.company.isNotBlank()) " • ${session.company}" else ""
        drawParagraph(meta, bodyPaint, 6f)
        drawParagraph(
            "Overall Score: ${String.format(Locale.US, "%.1f", session.overallScore)} / 10 (${session.mode} Mode)",
            headingPaint,
            10f
        )
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 14f

        if (session.topStrengths.isNotBlank()) {
            drawParagraph("Top Strengths", headingPaint, 4f)
            session.topStrengths.split("||").filter { it.isNotBlank() }.forEach {
                drawParagraph("• $it", bodyPaint, 3f)
            }
            y += 6f
        }

        if (session.topImprovements.isNotBlank()) {
            drawParagraph("Top Areas to Improve", headingPaint, 4f)
            session.topImprovements.split("||").filter { it.isNotBlank() }.forEach {
                drawParagraph("• $it", bodyPaint, 3f)
            }
            y += 6f
        }

        if (session.nextPracticePlan.isNotBlank()) {
            drawParagraph("Recommended Next Practice Plan", headingPaint, 4f)
            drawParagraph(session.nextPracticePlan, bodyPaint, 10f)
        }

        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 14f
        drawParagraph("Question Breakdown", headingPaint, 8f)

        turns.forEachIndexed { idx, turn ->
            drawParagraph("Q${idx + 1} [${turn.topic}]: ${turn.question}", headingPaint, 4f)
            drawParagraph("Answer: ${turn.userAnswer.ifBlank { "(Skipped)" }}", bodyPaint, 4f)
            drawParagraph(
                "Scores — Clarity ${turn.clarityScore}/10 • Structure ${turn.structureScore}/10 • " +
                    "Relevance ${turn.relevanceScore}/10 • Depth ${turn.depthScore}/10 • Confidence ${turn.confidenceScore}/10",
                bodyPaint,
                4f
            )
            if (turn.idealAnswer.isNotBlank()) {
                drawParagraph("Ideal Answer: ${turn.idealAnswer}", bodyPaint, 10f)
            } else {
                y += 6f
            }
        }

        pdfDocument.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            pdfDocument.writeTo(stream)
        } ?: error("Could not open output stream for selected file.")
        pdfDocument.close()
    }
}
