package net.osmand.plus.settings.backend;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.R;
import net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin;
import net.osmand.plus.backup.RemoteFile;
import net.osmand.plus.plugins.osmedit.OsmEditingPlugin;
import net.osmand.plus.settings.backend.backup.SettingsItemType;
import net.osmand.plus.settings.backend.backup.items.FileSettingsItem;
import net.osmand.plus.settings.backend.backup.items.FileSettingsItem.FileSubtype;
import net.osmand.plus.settings.backend.backup.items.SettingsItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum ExportSettingsType {

	PROFILE(R.string.shared_string_profiles, R.drawable.ic_action_manage_profiles, SettingsItemType.PROFILE.name()),
	GLOBAL(R.string.osmand_settings, R.drawable.ic_action_settings, SettingsItemType.GLOBAL.name()),
	QUICK_ACTIONS(R.string.configure_screen_quick_action, R.drawable.ic_quick_action, SettingsItemType.QUICK_ACTIONS.name()),
	POI_TYPES(R.string.poi_dialog_poi_type, R.drawable.ic_action_info_dark, SettingsItemType.POI_UI_FILTERS.name()),
	AVOID_ROADS(R.string.avoid_road, R.drawable.ic_action_alert, SettingsItemType.AVOID_ROADS.name()),
	FAVORITES(R.string.shared_string_favorites, R.drawable.ic_action_favorite, SettingsItemType.FAVOURITES.name()),
	FAVORITES_BACKUP(R.string.favorites_backup, R.drawable.ic_action_folder_favorites, SettingsItemType.FILE.name()),
	TRACKS(R.string.shared_string_tracks, R.drawable.ic_action_polygom_dark, SettingsItemType.GPX.name()),
	OSM_NOTES(R.string.osm_notes, R.drawable.ic_action_openstreetmap_logo, SettingsItemType.OSM_NOTES.name()),
	OSM_EDITS(R.string.osm_edits, R.drawable.ic_action_openstreetmap_logo, SettingsItemType.OSM_EDITS.name()),
	MULTIMEDIA_NOTES(R.string.notes, R.drawable.ic_grouped_by_type, SettingsItemType.FILE.name()),
	ACTIVE_MARKERS(R.string.map_markers, R.drawable.ic_action_flag, SettingsItemType.ACTIVE_MARKERS.name()),
	HISTORY_MARKERS(R.string.markers_history, R.drawable.ic_action_flag, SettingsItemType.HISTORY_MARKERS.name()),
	SEARCH_HISTORY(R.string.shared_string_search_history, R.drawable.ic_action_history, SettingsItemType.SEARCH_HISTORY.name()),
	NAVIGATION_HISTORY(R.string.navigation_history, R.drawable.ic_action_gdirections_dark, SettingsItemType.NAVIGATION_HISTORY.name()),
	CUSTOM_RENDER_STYLE(R.string.shared_string_rendering_style, R.drawable.ic_action_map_style, SettingsItemType.FILE.name()),
	CUSTOM_ROUTING(R.string.shared_string_routing, R.drawable.ic_action_route_distance, SettingsItemType.FILE.name()),
	MAP_SOURCES(R.string.quick_action_map_source_title, R.drawable.ic_map, SettingsItemType.MAP_SOURCES.name()),
	OFFLINE_MAPS(R.string.shared_string_maps, R.drawable.ic_map, SettingsItemType.FILE.name()),
	TTS_VOICE(R.string.local_indexes_cat_tts, R.drawable.ic_action_volume_up, SettingsItemType.FILE.name()),
	VOICE(R.string.local_indexes_cat_voice, R.drawable.ic_action_volume_up, SettingsItemType.FILE.name()),
	ONLINE_ROUTING_ENGINES(R.string.online_routing_engines, R.drawable.ic_world_globe_dark, SettingsItemType.ONLINE_ROUTING_ENGINES.name()),
	ITINERARY_GROUPS(R.string.shared_string_itinerary, R.drawable.ic_action_flag, SettingsItemType.ITINERARY_GROUPS.name());

	@StringRes
	private final int titleId;
	@DrawableRes
	private final int drawableRes;
	private final String itemName;

	ExportSettingsType(@StringRes int titleId, @DrawableRes int drawableRes, String itemName) {
		this.titleId = titleId;
		this.drawableRes = drawableRes;
		this.itemName = itemName;
	}

	@StringRes
	public int getTitleId() {
		return titleId;
	}

	@DrawableRes
	public int getIconRes() {
		return drawableRes;
	}

	public String getItemName() {
		return itemName;
	}

	public boolean isSettingsCategory() {
		return this == PROFILE || this == GLOBAL || this == QUICK_ACTIONS || this == POI_TYPES
				|| this == AVOID_ROADS;
	}

	public boolean isMyPlacesCategory() {
		return this == FAVORITES || this == TRACKS || this == OSM_EDITS || this == OSM_NOTES
				|| this == MULTIMEDIA_NOTES || this == ACTIVE_MARKERS || this == HISTORY_MARKERS
				|| this == SEARCH_HISTORY || this == ITINERARY_GROUPS || this == NAVIGATION_HISTORY;
	}

	public boolean isResourcesCategory() {
		return this == CUSTOM_RENDER_STYLE || this == CUSTOM_ROUTING || this == MAP_SOURCES
				|| this == OFFLINE_MAPS || this == VOICE || this == TTS_VOICE
				|| this == ONLINE_ROUTING_ENGINES || this == FAVORITES_BACKUP;
	}

	@Nullable
	public static ExportSettingsType getExportSettingsTypeForItem(@NonNull SettingsItem item) {
		for (ExportSettingsType exportType : values()) {
			if (exportType.getItemName().equals(item.getType().name())) {
				if (item.getType() == SettingsItemType.FILE) {
					FileSettingsItem fileItem = (FileSettingsItem) item;
					return getExportSettingsTypeFileSubtype(fileItem.getSubtype());
				} else {
					return exportType;
				}
			}
		}
		return null;
	}

	@Nullable
	public static ExportSettingsType getExportSettingsTypeForRemoteFile(@NonNull RemoteFile remoteFile) {
		if (remoteFile.item != null) {
			return getExportSettingsTypeForItem(remoteFile.item);
		}
		for (ExportSettingsType exportType : values()) {
			String type = remoteFile.getType();
			if (exportType.getItemName().equals(type)) {
				if (SettingsItemType.FILE.name().equals(type)) {
					FileSubtype subtype = FileSubtype.getSubtypeByFileName(remoteFile.getName());
					if (subtype != null) {
						return getExportSettingsTypeFileSubtype(subtype);
					}
				} else {
					return exportType;
				}
			}
		}
		return null;
	}

	public static ExportSettingsType getExportSettingsTypeFileSubtype(@NonNull FileSubtype subtype) {
		if (subtype == FileSubtype.RENDERING_STYLE) {
			return CUSTOM_RENDER_STYLE;
		} else if (subtype == FileSubtype.ROUTING_CONFIG) {
			return CUSTOM_ROUTING;
		} else if (subtype == FileSubtype.MULTIMEDIA_NOTES) {
			return MULTIMEDIA_NOTES;
		} else if (subtype == FileSubtype.GPX) {
			return TRACKS;
		} else if (subtype.isMap()) {
			return OFFLINE_MAPS;
		} else if (subtype == FileSubtype.TTS_VOICE) {
			return TTS_VOICE;
		} else if (subtype == FileSubtype.VOICE) {
			return VOICE;
		} else if (subtype == FileSubtype.FAVORITES_BACKUP) {
			return FAVORITES_BACKUP;
		}
		return null;
	}

	public static List<ExportSettingsType> getEnabledTypes() {
		List<ExportSettingsType> result = new ArrayList<>(Arrays.asList(values()));
		OsmEditingPlugin osmEditingPlugin = PluginsHelper.getActivePlugin(OsmEditingPlugin.class);
		if (osmEditingPlugin == null) {
			result.remove(OSM_EDITS);
			result.remove(OSM_NOTES);
		}
		AudioVideoNotesPlugin avNotesPlugin = PluginsHelper.getActivePlugin(AudioVideoNotesPlugin.class);
		if (avNotesPlugin == null) {
			result.remove(MULTIMEDIA_NOTES);
		}
		return result;
	}

	public static boolean isTypeEnabled(@NonNull ExportSettingsType type) {
		return getEnabledTypes().contains(type);
	}
}