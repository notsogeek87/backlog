package com.davidgcd.backlog.data.repository

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * An OkHttp client that never touches the network: [handler] gets each request's URL and answers a
 * body (200), a status code, or throws to simulate being offline. Records every URL it was asked.
 */
class FakeHttp(private val handler: (url: String) -> Any) {
    val requests = mutableListOf<String>()

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val url = chain.request().url.toString()
        requests += url
        when (val answer = handler(url)) {
            is Int -> response(chain, answer, "")
            is IOException -> throw answer
            else -> response(chain, 200, answer.toString())
        }
    }).build()

    private fun response(chain: Interceptor.Chain, code: Int, body: String) = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("fake")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
}

object Fixtures {
    const val DUNE_SEARCH = """{"numFound":1,"docs":[{"key":"/works/OL893415W","title":"Dune","author_name":["Frank Herbert"],
      "first_publish_year":1965,"cover_i":8231856,"cover_edition_key":"OL7353617M","subject":["Science fiction"]}]}"""

    const val DUNE_EDITION = """{"key":"/books/OL7353617M","title":"Dune","publishers":["Pocket"],"number_of_pages":736,
      "isbn_13":["9782070368228"],"isbn_10":["207036822X"],"languages":[{"key":"/languages/fre"}],"works":[{"key":"/works/OL893415W"}]}"""

    const val DUNE_WORK = """{"key":"/works/OL893415W","title":"Dune","description":{"type":"/type/text","value":"Sur Arrakis."}}"""

    const val DUNE_EDITIONS = """{"entries":[
      {"key":"/books/OL7353617M","title":"Dune","publishers":["Pocket"],"isbn_13":["9782070368228"]},
      {"key":"/books/OL9M","title":"Dune","publishers":["Ace"],"isbn_13":["9780441172719"]}]}"""

    const val GOOGLE_SEARCH = """{"items":[{"id":"g1","volumeInfo":{"title":"Fondation","authors":["Isaac Asimov"],
      "industryIdentifiers":[{"type":"ISBN_13","identifier":"9782070360536"}]}}]}"""

    const val EMPTY_OPEN_LIBRARY = """{"numFound":0,"docs":[]}"""
    const val EMPTY_GOOGLE = """{"totalItems":0}"""
}
