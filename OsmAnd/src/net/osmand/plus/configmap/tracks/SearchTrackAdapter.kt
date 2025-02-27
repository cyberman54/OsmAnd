package net.osmand.plus.configmap.tracks

import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import net.osmand.CallbackWithObject
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.configmap.tracks.viewholders.*
import net.osmand.plus.configmap.tracks.viewholders.EmptyTracksViewHolder.ImportTracksListener
import net.osmand.plus.configmap.tracks.viewholders.SortTracksViewHolder.SortTracksListener
import net.osmand.plus.configmap.tracks.viewholders.TrackViewHolder.TrackSelectionListener
import net.osmand.plus.myplaces.tracks.TracksSearchFilter
import net.osmand.plus.settings.enums.TracksSortMode
import net.osmand.plus.utils.ColorUtilities
import net.osmand.plus.utils.UiUtilities
import net.osmand.plus.utils.UpdateLocationUtils
import net.osmand.plus.utils.UpdateLocationUtils.UpdateLocationViewCache
import net.osmand.util.Algorithms
import java.util.*

class SearchTracksAdapter(
    private val app: OsmandApplication,
    private val trackItems: List<TrackItem>,
    private val nightMode: Boolean
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable {

    private val locationViewCache: UpdateLocationViewCache
    private val filter: TracksSearchFilter = TracksSearchFilter(trackItems)

    private var items: MutableList<Any> = mutableListOf()
    private var filteredItems: List<TrackItem> = mutableListOf()
    private var sortMode: TracksSortMode = TracksSortMode.getDefaultSortMode()

    private var sortTracksListener: SortTracksListener? = null
    private var selectionListener: TrackSelectionListener? = null
    private var importTracksListener: ImportTracksListener? = null

    init {
        updateFilteredItems(trackItems)
        locationViewCache = UpdateLocationUtils.getUpdateLocationViewCache(app)
        locationViewCache.arrowResId = R.drawable.ic_direction_arrow
        locationViewCache.arrowColor = ColorUtilities.getActiveIconColorId(nightMode)
    }

    fun getFilteredItems(): List<TrackItem> {
        return ArrayList(filteredItems)
    }

    fun setTracksSortMode(sortMode: TracksSortMode) {
        this.sortMode = sortMode
        val latLon = app.mapViewTrackingUtilities.defaultLocation
        Collections.sort(items, TracksComparator(sortMode, latLon))
    }

    fun setSortTracksListener(sortTracksListener: SortTracksListener?) {
        this.sortTracksListener = sortTracksListener
    }

    fun setSelectionListener(selectionListener: TrackSelectionListener?) {
        this.selectionListener = selectionListener
    }

    fun setImportTracksListener(importTracksListener: ImportTracksListener?) {
        this.importTracksListener = importTracksListener
    }

    fun setFilterCallback(filterCallback: CallbackWithObject<List<TrackItem>>) {
        filter.setCallback(filterCallback)
    }

    fun updateFilteredItems(filteredItems: List<TrackItem>) {
        this.filteredItems = filteredItems

        items = ArrayList<Any>()
        items.add(TracksAdapter.TYPE_SORT_TRACKS)

        if (Algorithms.isEmpty(trackItems)) {
            items.add(TracksAdapter.TYPE_NO_TRACKS)
        } else if (Algorithms.isEmpty(filteredItems)) {
            items.add(TYPE_NO_FOUND_TRACKS)
        } else {
            items.addAll(filteredItems)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = UiUtilities.getInflater(parent.context, nightMode)
        return when (viewType) {
            TracksAdapter.TYPE_TRACK -> {
                val view = inflater.inflate(R.layout.track_list_item, parent, false)
                TrackViewHolder(view, selectionListener, locationViewCache, nightMode)
            }
            TracksAdapter.TYPE_NO_TRACKS -> {
                val view = inflater.inflate(R.layout.empty_state, parent, false)
                EmptyTracksViewHolder(view, importTracksListener, nightMode)
            }
            TYPE_NO_FOUND_TRACKS -> {
                val view = inflater.inflate(R.layout.empty_search_results, parent, false)
                EmptySearchResultViewHolder(view)
            }
            TracksAdapter.TYPE_SORT_TRACKS -> {
                val view = inflater.inflate(R.layout.sort_type_view, parent, false)
                SortTracksViewHolder(view, sortTracksListener, nightMode)
            }
            else -> throw IllegalArgumentException("Unsupported view type $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        val any = items[position]
        if (any is TrackItem) {
            return TracksAdapter.TYPE_TRACK
        } else if (any is Int) {
            if (TracksAdapter.TYPE_NO_TRACKS == any) {
                return TracksAdapter.TYPE_NO_TRACKS
            } else if (TracksAdapter.TYPE_SORT_TRACKS == any) {
                return TracksAdapter.TYPE_SORT_TRACKS
            } else if (TYPE_NO_FOUND_TRACKS == any) {
                return TYPE_NO_FOUND_TRACKS
            }
        }
        throw IllegalArgumentException("Unsupported view type")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TrackViewHolder) {
            val item = items[position] as TrackItem
            val showDivider = position != itemCount - 1
            holder.bindView(sortMode, item, showDivider, true, true)
        } else if (holder is NoVisibleTracksViewHolder) {
            holder.bindView()
        } else if (holder is EmptyTracksViewHolder) {
            holder.bindView()
        } else if (holder is RecentlyVisibleViewHolder) {
            holder.bindView()
        } else if (holder is SortTracksViewHolder) {
            val enabled = !Algorithms.isEmpty(trackItems)
            holder.bindView(enabled)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getFilter(): Filter {
        return filter
    }

    companion object {
        const val TYPE_NO_FOUND_TRACKS = 5
    }
}