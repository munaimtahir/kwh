package com.example.kwh.ui.history

import androidx.lifecycle.ViewModel

/**
 * ViewModel backing the history import/export flows.
 *
 * Only a minimal CSV parser is implemented for testing purposes. The parser expects the CSV to
 * contain a header row followed by one row per reading with the following columns:
 * 1. meter id (Long)
 * 2. recorded at timestamp (epoch millis as Long)
 * 3. reading value (Double)
 * 4. optional notes column
 */
class HistoryViewModel : ViewModel() {

    data class CsvRow(
        val meterId: Long,
        val recordedAtEpochMillis: Long,
        val value: Double,
        val notes: String?
    )

    class CsvParser {
        fun parse(csv: String): List<CsvRow> {
            if (csv.isBlank()) return emptyList()

            val rows = mutableListOf<CsvRow>()
            val iterator = csv.splitToSequence('\n').iterator()

            if (!iterator.hasNext()) {
                return emptyList()
            }

            // Skip the header row
            iterator.next()

            while (iterator.hasNext()) {
                val rawLine = iterator.next().trimEnd('\r')
                if (rawLine.isBlank()) continue

                val columns = rawLine.split(',')
                require(columns.size >= 3) { "CSV row must contain at least 3 columns" }

                val meterId = columns[0].trim().toLong()
                val recordedAt = columns[1].trim().toLong()
                val value = columns[2].trim().toDouble()
                val notes = columns.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }

                rows.add(CsvRow(meterId, recordedAt, value, notes))
            }

            return rows
        }
    }
}

