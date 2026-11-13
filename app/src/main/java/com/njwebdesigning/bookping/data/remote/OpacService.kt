
package com.njwebdesigning.bookping.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
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
                            "Check whether the OPAC login/page is accessible."
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

            // Find the correct book table by its headers
            val bookTable = document.select("table")
                .firstOrNull { table ->

                    val headers = table.select("thead th")
                        .map { it.text().trim() }

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
                ?: error(
                    "Book table not found in OPAC response.\n" +
                            "Page title: ${document.title()}\n" +
                            "Final URL: ${document.location()}\n" +
                            "Response preview: ${
                                document.body()?.text()?.take(500)
                            }"
                )

            // STEP 5: Extract table headers
            val headers = bookTable
                .select("thead th")
                .map { it.text().trim() }

            fun columnIndex(name: String): Int {

                val index = headers.indexOfFirst {
                    it.contains(
                        name,
                        ignoreCase = true
                    )
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

            // STEP 6: Convert table rows into OpacBook
            val rows = bookTable.select("tbody tr")

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

    // STEP 7: Convert OPAC date into milliseconds
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
