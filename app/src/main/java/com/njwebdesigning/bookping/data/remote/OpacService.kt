
package com.njwebdesigning.bookping.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.CookieManager
import java.net.CookiePolicy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class OpacBook(
    val library: String,
    val accessionNumber: String,
    val title: String,
    val issueDateMillis: Long,
    val dueDateMillis: Long
)

class OpacService {

    companion object {
        private const val OPAC_URL =
            "http://115.244.103.83/webopac/member/dues"

        private const val OPAC_ORIGIN =
            "http://115.244.103.83"
    }

    private val cookieManager = CookieManager(
        null,
        CookiePolicy.ACCEPT_ALL
    )

    private val client = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchBooks(memberId: String): List<OpacBook> =
        withContext(Dispatchers.IO) {

            require(memberId.isNotBlank()) {
                "Member ID cannot be empty"
            }

            // STEP 1: GET OPAC page

            val getRequest = Request.Builder()
                .url(OPAC_URL)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 Android BookPing"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml"
                )
                .get()
                .build()

            val initialHtml = client.newCall(getRequest)
                .execute().use { response ->

                    if (!response.isSuccessful) {
                        error(
                            "OPAC page request failed: ${response.code}"
                        )
                    }

                    response.body?.string()
                        ?: error("Empty OPAC response")
                }

            // STEP 2: Extract security token

            val initialDocument = Jsoup.parse(
                initialHtml,
                OPAC_URL
            )

            val token = initialDocument
                .selectFirst(
                    "input[name=__RequestVerificationToken]"
                )
                ?.attr("value")
                ?.takeIf { it.isNotBlank() }
                ?: error(
                    "OPAC security token not found. " +
                            "Check whether the OPAC page is accessible."
                )

            // STEP 3: Submit member ID

            val formBody = FormBody.Builder()
                .add("MemberID", memberId.trim())
                .add("__RequestVerificationToken", token)
                .build()

            val postRequest = Request.Builder()
                .url(OPAC_URL)
                .header("Referer", OPAC_URL)
                .header("Origin", OPAC_ORIGIN)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 Android BookPing"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml"
                )
                .post(formBody)
                .build()

            val responseHtml = client.newCall(postRequest)
                .execute().use { response ->

                    if (!response.isSuccessful) {
                        error(
                            "OPAC search failed: ${response.code}"
                        )
                    }

                    response.body?.string()
                        ?: error("Empty OPAC search response")
                }

            // STEP 4: Parse returned HTML

            val document = Jsoup.parse(
                responseHtml,
                OPAC_URL
            )

            val pageText = document.body()
                .text()
                .replace(Regex("\\s+"), " ")
                .trim()

            val normalizedText = pageText.lowercase(Locale.ROOT)

            // IMPORTANT:
            // An empty circulation result is a successful response,
            // not a parsing error.

            val noBooksMessages = listOf(
                "no books found under circulation for the member",
                "no books found",
                "no books are currently issued"
            )

            if (noBooksMessages.any {
                    normalizedText.contains(it)
                }
            ) {
                return@withContext emptyList()
            }

            // STEP 5: Find the book table using its headers

            val bookTable = document.select("table")
                .firstOrNull { table ->

                    val headerCells = table.select(
                        "thead th, thead td"
                    ).ifEmpty {
                        table.select("tr")
                            .firstOrNull()
                            ?.select("th, td")
                            .orEmpty()
                    }

                    val headers = headerCells.map {
                        it.text().trim()
                    }

                    headers.any {
                        it.contains(
                            "Acc. Number",
                            ignoreCase = true
                        )
                    } && headers.any {
                        it.contains(
                            "Due Date",
                            ignoreCase = true
                        )
                    }
                }

            if (bookTable == null) {
                error(
                    "Unable to recognize the OPAC response. " +
                            "The page did not contain a book table " +
                            "or a recognized empty-book message."
                )
            }

            // STEP 6: Extract table headers

            val headerRow = bookTable.select("thead tr")
                .firstOrNull()
                ?: bookTable.select("tr").firstOrNull()
                ?: error("OPAC table header not found")

            val headers = headerRow.select("th, td")
                .map { it.text().trim() }

            fun columnIndex(name: String): Int {
                val index = headers.indexOfFirst {
                    it.contains(name, ignoreCase = true)
                }

                if (index < 0) {
                    error("Missing OPAC column: $name")
                }

                return index
            }

            val libraryIndex = columnIndex("Library")
            val accessionIndex = columnIndex("Acc. Number")
            val titleIndex = columnIndex("Title")
            val issueIndex = columnIndex("Issue Date")
            val dueIndex = columnIndex("Due Date")

            // STEP 7: Extract book rows

            val rows = bookTable.select("tbody tr")
                .ifEmpty {
                    bookTable.select("tr").drop(1)
                }

            rows.mapNotNull { row ->

                val cells = row.select("td")

                if (cells.size < headers.size) {
                    return@mapNotNull null
                }

                val accession =
                    cells[accessionIndex].text().trim()

                val title =
                    cells[titleIndex].text().trim()

                if (accession.isBlank() || title.isBlank()) {
                    return@mapNotNull null
                }

                OpacBook(
                    library = cells[libraryIndex].text().trim(),
                    accessionNumber = accession,
                    title = title,
                    issueDateMillis = parseOpacDate(
                        cells[issueIndex].text()
                    ),
                    dueDateMillis = parseOpacDate(
                        cells[dueIndex].text()
                    )
                )
            }
        }

    // STEP 8: Convert OPAC date into milliseconds

    private fun parseOpacDate(value: String): Long {

        val formatter = SimpleDateFormat(
            "dd/MM/yyyy",
            Locale.US
        ).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }

        return formatter.parse(value.trim())?.time
            ?: error("Invalid OPAC date: $value")
    }
}
