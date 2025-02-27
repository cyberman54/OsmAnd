package net.osmand.plus.settings.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.CollapsingToolbarLayout;

import net.osmand.IProgress;
import net.osmand.PlatformUtil;
import net.osmand.plus.AppInitializer;
import net.osmand.plus.R;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin;
import net.osmand.plus.resources.ResourceManager.ReloadIndexesListener;
import net.osmand.plus.settings.backend.backup.SettingsHelper;
import net.osmand.plus.settings.backend.backup.SettingsHelper.CheckDuplicatesListener;
import net.osmand.plus.settings.backend.backup.SettingsHelper.ImportListener;
import net.osmand.plus.settings.backend.backup.items.FileSettingsItem;
import net.osmand.plus.settings.backend.backup.items.SettingsItem;

import org.apache.commons.logging.Log;

import java.util.List;

public abstract class ImportSettingsFragment extends BaseSettingsListFragment {

	public static final String TAG = ImportSettingsFragment.class.getSimpleName();
	public static final Log LOG = PlatformUtil.getLog(ImportSettingsFragment.class.getSimpleName());

	protected static final String DUPLICATES_START_TIME_KEY = "duplicates_start_time";
	protected static final long MIN_DELAY_TIME_MS = 500;

	protected List<SettingsItem> settingsItems;

	protected TextView description;
	protected ProgressBar progressBar;
	protected LinearLayout buttonsContainer;
	protected CollapsingToolbarLayout toolbarLayout;

	protected long duplicateStartTime;

	public void setSettingsItems(List<SettingsItem> settingsItems) {
		this.settingsItems = settingsItems;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			duplicateStartTime = savedInstanceState.getLong(DUPLICATES_START_TIME_KEY);
		}
		if (settingsItems != null) {
			dataList = SettingsHelper.getSettingsToOperateByCategory(settingsItems, false, false);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		if (view != null) {
			toolbarLayout = view.findViewById(R.id.toolbar_layout);
			buttonsContainer = view.findViewById(R.id.buttons_container);
			progressBar = view.findViewById(R.id.progress_bar);

			description = header.findViewById(R.id.description);
			description.setText(R.string.select_data_to_import);
		}
		return view;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(DUPLICATES_START_TIME_KEY, duplicateStartTime);
	}

	protected abstract void processDuplicates(List<Object> duplicates, List<SettingsItem> items);

	protected abstract void importFinished(boolean succeed, boolean needRestart, List<SettingsItem> items);

	public ImportListener getImportListener() {
		return new ImportListener() {
			@Override
			public void onImportFinished(boolean succeed, boolean needRestart, @NonNull List<SettingsItem> items) {
				if (succeed) {
					app.getRendererRegistry().updateExternalRenderers();
					AppInitializer.loadRoutingFiles(app, null);
					reloadIndexes(items);
					AudioVideoNotesPlugin plugin = PluginsHelper.getPlugin(AudioVideoNotesPlugin.class);
					if (plugin != null) {
						plugin.indexingFiles(true, true);
					}
				}
				importFinished(succeed, needRestart, items);
			}
		};
	}

	protected void reloadIndexes(@NonNull List<SettingsItem> items) {
		for (SettingsItem item : items) {
			if (item instanceof FileSettingsItem && ((FileSettingsItem) item).getSubtype().isMap()) {
				app.getResourceManager().reloadIndexesAsync(IProgress.EMPTY_PROGRESS, new ReloadIndexesListener() {
					@Override
					public void reloadIndexesStarted() {
					}

					@Override
					public void reloadIndexesFinished(List<String> warnings) {
						app.getOsmandMap().refreshMap();
					}
				});
				break;
			}
		}
	}

	protected CheckDuplicatesListener getDuplicatesListener() {
		return (duplicates, items) -> {
			long spentTime = System.currentTimeMillis() - duplicateStartTime;
			if (spentTime < MIN_DELAY_TIME_MS) {
				long delay = MIN_DELAY_TIME_MS - spentTime;
				app.runInUIThread(() -> processDuplicates(duplicates, items), delay);
			} else {
				processDuplicates(duplicates, items);
			}
		};
	}
}
