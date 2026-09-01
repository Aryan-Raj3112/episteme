package com.aryan.reader.shared.ios

import com.aryan.reader.shared.opds.SharedOpdsStreamRequest
import com.aryan.reader.shared.ui.SharedMobileEpubStreamPageLoader
import com.aryan.reader.shared.ui.SharedMobileEpubStreamPageResponse
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolves credential-free WebView resource URLs through the authenticated OPDS
 * repository. Credentials stay in the catalog store and never enter generated HTML.
 */
internal class IosOpdsStreamPageLoader(
    private val repository: IosOpdsRepository,
) : SharedMobileEpubStreamPageLoader {
    override suspend fun loadPage(resourceUrl: String): SharedMobileEpubStreamPageResponse? {
        val request = SharedOpdsStreamRequest.parseResourceUri(resourceUrl) ?: return null
        val page = try {
            repository.fetchStreamPageData(
                reference = request.reference,
                pageIndex = request.pageIndex,
                maxWidth = request.maxWidth,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // The WebKit scheme handler renders the localized dark placeholder
            // when a page cannot be fetched.
            return null
        } ?: return null
        return SharedMobileEpubStreamPageResponse(
            bytes = page.bytes,
            mimeType = page.mimeType,
        )
    }
}
