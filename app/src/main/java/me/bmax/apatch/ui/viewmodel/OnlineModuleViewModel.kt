package me.bmax.apatch.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.bmax.apatch.util.InstalledModule
import me.bmax.apatch.util.ModuleRepo
import me.bmax.apatch.util.ModuleStore

private const val TAG = "OnlineModuleViewModel"

/**
 * How many module documents are read at once. The repository rate limits per
 * client, and a page of 30 entries fetched in one burst would brush against it.
 */
private const val MAX_PARALLEL_DOCS = 3

/** Which half of the catalogue the listing shows. */
enum class OnlineCategory(val kpm: Boolean?) {
    ALL(null), SYSTEM(false), KPM(true);

    companion object {
        fun of(kpmOnly: Boolean): OnlineCategory = if (kpmOnly) KPM else ALL
    }
}

/**
 * One row of the online listing: the catalogue entry, its document once it has
 * been read, and what the local stores say about it.
 */
data class OnlineModule(
    val repo: ModuleRepo.RepoModule,
    val doc: ModuleRepo.RepoModuleDoc? = null,
    val installedModule: InstalledModule? = null,
    val updateAvailable: Boolean = false,
) {
    val id: String get() = repo.id
    val kpm: Boolean get() = repo.kpm
    val stars: Int get() = repo.stars
    val installed: Boolean get() = installedModule != null
    val installedVersion: String get() = installedModule?.version.orEmpty()
    val title: String get() = doc?.name?.takeIf { it.isNotBlank() } ?: repo.id
    val version: String get() = doc?.version.orEmpty()
    val author: String get() = doc?.authors?.firstOrNull { it.isNotBlank() } ?: repo.author
    val summary: String
        get() = doc?.summaryText()?.takeIf { it.isNotBlank() } ?: repo.description
    val downloadUrl: String get() = doc?.downloadUrl.orEmpty()
    val canInstall: Boolean get() = downloadUrl.isNotBlank()
}

/**
 * Drives the online listing: pages the catalogue, reads each entry's document
 * and compares it with what is installed.
 */
class OnlineModuleViewModel : ViewModel() {
    var category by mutableStateOf(OnlineCategory.ALL)
    var search by mutableStateOf("")
        private set

    var modules by mutableStateOf<List<OnlineModule>>(emptyList())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf("")
        private set

    /** Set once the destination has told us which category it was opened for. */
    private var entered = false

    /** The next page to read; refresh resets it to 2 once page 1 is in. */
    private var nextPage = 1

    /** Pages in flight are dropped when a newer load starts. */
    private var generation = 0
    private var pageJob: Job? = null
    private var searchJob: Job? = null
    private var enrichJob: Job? = null

    /** Called when the screen is entered; refreshes on every visit. */
    fun onEnter(kpmOnly: Boolean) {
        if (!entered) {
            entered = true
            category = OnlineCategory.of(kpmOnly)
        }
        refresh()
    }

    fun onSearchChange(text: String) {
        if (text == search) return
        search = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            refresh()
        }
    }

    fun onCategoryChange(value: OnlineCategory) {
        if (category == value) return
        category = value
        searchJob?.cancel()
        refresh()
    }

    fun refresh() {
        pageJob?.cancel()
        enrichJob?.cancel()
        generation++
        val gen = generation
        isRefreshing = true
        errorMessage = ""
        pageJob = viewModelScope.launch(Dispatchers.IO) {
            val installed = installedModules()
            val fetched = runCatching {
                ModuleRepo.listModules(1, ModuleRepo.PAGE_SIZE, category.kpm, search)
            }
            if (gen != generation) return@launch
            val listed = fetched.getOrElse { error ->
                Log.w(TAG, "load online modules failed", error)
                modules = emptyList()
                hasMore = false
                errorMessage = error.message ?: error.javaClass.simpleName
                isRefreshing = false
                return@launch
            }
            val rows = listed.map { newRow(it, installed) }
            modules = rows
            hasMore = listed.size >= ModuleRepo.PAGE_SIZE
            nextPage = 2
            isRefreshing = false
            // Documents arrive one after another so each row can show the
            // catalogue description while its details are still on the way.
            enrich(gen, rows)
        }
    }

    fun loadMore() {
        if (isRefreshing || isLoadingMore || !hasMore || pageJob?.isActive == true) return
        val gen = generation
        val target = nextPage
        isLoadingMore = true
        pageJob = viewModelScope.launch(Dispatchers.IO) {
            val installed = installedModules()
            val fetched = runCatching {
                ModuleRepo.listModules(target, ModuleRepo.PAGE_SIZE, category.kpm, search)
            }
            if (gen != generation) return@launch
            val listed = fetched.getOrElse { error ->
                Log.w(TAG, "load page $target failed", error)
                errorMessage = error.message ?: error.javaClass.simpleName
                isLoadingMore = false
                return@launch
            }
            val rows = listed.map { newRow(it, installed) }
            modules = modules + rows
            hasMore = listed.size >= ModuleRepo.PAGE_SIZE
            nextPage = target + 1
            isLoadingMore = false
            enrich(gen, rows)
        }
    }

    /** Reads the documents of [rows] and folds the details into the listing. */
    private fun enrich(gen: Int, rows: List<OnlineModule>) {
        if (rows.isEmpty()) return
        enrichJob = viewModelScope.launch(Dispatchers.IO) {
            coroutineScope {
                val permits = Semaphore(MAX_PARALLEL_DOCS)
                rows.map { row ->
                    async {
                        permits.withPermit {
                            val doc = ModuleRepo.moduleDoc(row.id, row.kpm) ?: return@withPermit
                            if (gen != generation) return@withPermit
                            val installed = row.installedModule
                            modules = modules.map { current ->
                                if (current.id == row.id && current.kpm == row.kpm) current.copy(
                                    doc = doc,
                                    updateAvailable = installed != null && doc.downloadUrl.isNotBlank() &&
                                        ModuleRepo.isNewerVersion(doc, installed.version, installed.versionCode),
                                ) else current
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun newRow(
        repo: ModuleRepo.RepoModule,
        installed: Pair<Map<String, InstalledModule>, Map<String, InstalledModule>>,
    ): OnlineModule {
        val (system, kpm) = installed
        val module = if (repo.kpm) kpm[repo.id] else system[repo.id]
        return OnlineModule(repo = repo, installedModule = module)
    }

    /**
     * Both stores in one pair, keyed the way the repository keys its entries: a
     * system module by its directory id, a KPM by the id derived from its
     * kptools name.
     */
    private fun installedModules(): Pair<Map<String, InstalledModule>, Map<String, InstalledModule>> =
        runCatching { ModuleStore.listSystemModules() to ModuleStore.listKpmModules() }
            .getOrDefault(emptyMap<String, InstalledModule>() to emptyMap())

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 450L
    }
}
