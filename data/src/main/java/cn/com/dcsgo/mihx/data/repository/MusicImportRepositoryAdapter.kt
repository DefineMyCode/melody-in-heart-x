package cn.com.dcsgo.mihx.data.repository

import android.net.Uri
import cn.com.dcsgo.mihx.domain.repository.MusicImportRepository
import javax.inject.Inject

class MusicImportRepositoryAdapter @Inject constructor(
    private val musicRepository: MusicRepository,
) : MusicImportRepository {
    override suspend fun importFolder(
        treeUri: Uri,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): Int = musicRepository.importFolder(treeUri, onProgress)
}
