package dev.musicforge.playback

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.musicforge.data.db.ArtistEntity
import dev.musicforge.data.db.MusicDatabase
import dev.musicforge.data.db.PlaylistEntity
import dev.musicforge.data.db.PlaylistSongMap
import dev.musicforge.data.db.SongArtistMap
import dev.musicforge.data.db.SongEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowseTreeTest {
    private lateinit var db: MusicDatabase
    private lateinit var tree: BrowseTree

    @Before fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MusicDatabase::class.java).allowMainThreadQueries().build()
        tree = BrowseTree(db.dao())
        val dao = db.dao()
        dao.upsert(ArtistEntity(id = "a1", name = "Portishead"))
        dao.upsert(SongEntity(id = "s1", title = "Roads", liked = true, likedDate = 2, isDownloaded = true, dateDownload = 1))
        dao.upsert(SongEntity(id = "s2", title = "Sour Times", liked = false))
        dao.upsert(SongArtistMap("s1", "a1", 0))
        dao.insert(PlaylistEntity(id = "p1", name = "Night"))
        dao.insert(PlaylistSongMap(playlistId = "p1", songId = "s2", position = 0))
    }

    @After fun tearDown() = db.close()

    @Test fun rootHasFourFoldersAndTheyBrowse() = runBlocking<Unit> {
        assertThat(tree.root().mediaMetadata.isBrowsable).isTrue()
        val top = tree.children(BrowseTree.ROOT)
        assertThat(top.map { it.mediaId }).containsExactly("liked", "downloads", "recent", "playlists").inOrder()
        assertThat(top.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false }).isTrue()
    }

    @Test fun foldersHoldPlayableSongsWithMetadata() = runBlocking<Unit> {
        val liked = tree.children(BrowseTree.LIKED)
        assertThat(liked.map { it.mediaId }).containsExactly("s1")
        assertThat(liked.single().mediaMetadata.artist.toString()).isEqualTo("Portishead")
        assertThat(liked.single().mediaMetadata.isPlayable).isTrue()
        assertThat(tree.children(BrowseTree.DOWNLOADS).map { it.mediaId }).containsExactly("s1")
        val playlists = tree.children(BrowseTree.PLAYLISTS)
        assertThat(playlists.single().mediaMetadata.title.toString()).isEqualTo("Night")
        assertThat(tree.children(playlists.single().mediaId).map { it.mediaId }).containsExactly("s2")
    }

    @Test fun songRestoresMetadataForIdOnlyRequests() = runBlocking<Unit> {
        val item = tree.song("s1")!!
        assertThat(item.mediaMetadata.title.toString()).isEqualTo("Roads")
        assertThat(tree.song("missing")).isNull()
    }
}
