package com.repoplayer.app.source

import com.repoplayer.app.data.ContentItem
import com.repoplayer.app.data.Details
import com.repoplayer.app.data.Episode
import com.repoplayer.app.data.Row
import com.repoplayer.app.data.SourceEntry
import com.repoplayer.app.data.StreamLink
import com.repoplayer.app.data.Subtitle
import kotlinx.coroutines.flow.Flow

/** Uygulamanın tek bir kaynakla konuştuğu ortak arayüz */
interface ContentSource {
    val entry: SourceEntry

    /** Ana sayfa satırları; hazır oldukça akış halinde gelir */
    fun home(): Flow<Row>

    /** "Tümünü göster": bir satırın N. sayfası (1'den başlar) */
    suspend fun rowPage(row: Row, page: Int): List<ContentItem>

    suspend fun search(query: String): List<ContentItem>

    suspend fun details(item: ContentItem): Details

    /** Oynatma bağlantılarını bulundukça geri çağırır */
    suspend fun links(
        details: Details,
        episode: Episode?,
        onLink: (StreamLink) -> Unit,
        onSubtitle: (Subtitle) -> Unit,
    )
}
