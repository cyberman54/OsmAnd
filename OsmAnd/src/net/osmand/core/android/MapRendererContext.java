package net.osmand.core.android;

import android.util.Log;

import net.osmand.core.jni.ElevationConfiguration;
import net.osmand.core.jni.ElevationConfiguration.SlopeAlgorithm;
import net.osmand.core.jni.ElevationConfiguration.VisualizationStyle;
import net.osmand.core.jni.GeoTiffCollection;
import net.osmand.core.jni.IGeoTiffCollection.RasterType;
import net.osmand.core.jni.IMapTiledSymbolsProvider;
import net.osmand.core.jni.IObfsCollection;
import net.osmand.core.jni.IRasterMapLayerProvider;
import net.osmand.core.jni.MapObjectsSymbolsProvider;
import net.osmand.core.jni.MapPresentationEnvironment;
import net.osmand.core.jni.MapPresentationEnvironment.LanguagePreference;
import net.osmand.core.jni.MapPrimitivesProvider;
import net.osmand.core.jni.MapPrimitiviser;
import net.osmand.core.jni.MapRasterLayerProvider_Software;
import net.osmand.core.jni.MapStylesCollection;
import net.osmand.core.jni.ObfMapObjectsProvider;
import net.osmand.core.jni.QStringStringHash;
import net.osmand.core.jni.ResolvedMapStyle;
import net.osmand.core.jni.SqliteHeightmapTileProvider;
import net.osmand.core.jni.SwigUtilities;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.development.OsmandDevelopmentPlugin;
import net.osmand.plus.render.MapRenderRepositories;
import net.osmand.plus.render.RendererRegistry;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.utils.NativeUtilities;
import net.osmand.render.RenderingRuleProperty;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRuleStorageProperties;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.util.Algorithms;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static net.osmand.IndexConstants.GEOTIFF_DIR;
import static net.osmand.IndexConstants.GEOTIFF_SQLITE_CACHE_DIR;
import static net.osmand.plus.views.OsmandMapTileView.MAP_DEFAULT_COLOR;

/**
 * Context container and utility class for MapRendererView and derivatives.
 *
 * @author Alexey Pelykh
 */
public class MapRendererContext {
	private static final String TAG = "MapRendererContext";

	public static final int OBF_RASTER_LAYER = 0;
	public static final int OBF_SYMBOL_SECTION = 1;
	public static final int WEATHER_CONTOURS_SYMBOL_SECTION = 2;

	private final OsmandApplication app;

	// input parameters
	private MapStylesCollection mapStylesCollection;
	private IObfsCollection obfsCollection;

	private boolean nightMode;
	private boolean useAppLocale;
	private final float density;

	// сached objects
	private final Map<String, ResolvedMapStyle> mapStyles = new HashMap<>();
	private CachedMapPresentation presentationObjectParams;
	private MapPresentationEnvironment mapPresentationEnvironment;
	private MapPrimitiviser mapPrimitiviser;

	private IMapTiledSymbolsProvider obfMapSymbolsProvider;
	private IRasterMapLayerProvider obfMapRasterLayerProvider;
	@Nullable
	private GeoTiffCollection geoTiffCollection;
	private volatile MapRendererView mapRendererView;

	private float cachedReferenceTileSize;

	public MapRendererContext(OsmandApplication app, float density) {
		this.app = app;
		this.density = density;
	}

	/**
	 * Bounds specified map renderer view to this context
	 *
	 * @param mapRendererView Reference to MapRendererView
	 */
	public void setMapRendererView(@Nullable MapRendererView mapRendererView) {
		boolean update = (this.mapRendererView != mapRendererView);
		this.mapRendererView = mapRendererView;
		if (!update) {
			return;
		}
		if (mapRendererView != null) {
			applyCurrentContextToView();
		}
	}

	public boolean isVectorLayerEnabled() {
		return !app.getSettings().MAP_ONLINE_DATA.get();
	}

	public void setNightMode(boolean nightMode) {
		if (nightMode != this.nightMode) {
			this.nightMode = nightMode;
			updateMapSettings();
		}
	}

	public void updateLocalization() {
		int zoom = app.getOsmandMap().getMapView().getZoom();
		boolean useAppLocale = MapRenderRepositories.useAppLocaleForMap(app, zoom);
		if (this.useAppLocale != useAppLocale) {
			this.useAppLocale = useAppLocale;
			updateMapSettings();
		}
	}

	public void updateMapSettings() {
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView instanceof AtlasMapRendererView && cachedReferenceTileSize != getReferenceTileSize()) {
			((AtlasMapRendererView) mapRendererView).setReferenceTileSizeOnScreenInPixels(getReferenceTileSize());
		}
		if (mapPresentationEnvironment != null) {
			updateMapPresentationEnvironment();
		}
	}

	/**
	 * Setup OBF map on layer 0 with symbols
	 *
	 * @param obfsCollection OBFs collection
	 */
	public void setupObfMap(MapStylesCollection mapStylesCollection, IObfsCollection obfsCollection) {
		this.obfsCollection = obfsCollection;
		this.mapStylesCollection = mapStylesCollection;
		updateMapPresentationEnvironment();
		if (isVectorLayerEnabled()) {
			recreateRasterAndSymbolsProvider();
		}
	}

	protected int getRasterTileSize() {
		return (int) (getReferenceTileSize() * app.getSettings().MAP_DENSITY.get());
	}

	private float getReferenceTileSize() {
		return 256 * Math.max(1, density);
	}

	/**
	 * Update map presentation environment and everything that depends on it
	 */
	private void updateMapPresentationEnvironment() {
		// Create new map presentation environment
		OsmandSettings settings = app.getSettings();

		int zoom = app.getOsmandMap().getMapView().getZoom();
		String langId = MapRenderRepositories.getMapPreferredLocale(app, zoom);
		boolean transliterate = MapRenderRepositories.transliterateMapNames(app, zoom);
		LanguagePreference langPref = transliterate
				? LanguagePreference.LocalizedOrTransliterated
				: LanguagePreference.LocalizedOrNative;

		loadRendererAddons();
		String rendName = settings.RENDERER.get();
		if (rendName.length() == 0 || rendName.equals(RendererRegistry.DEFAULT_RENDER)) {
			rendName = "default";
		}
		if (!mapStyles.containsKey(rendName)) {
			Log.d(TAG, "Style '" + rendName + "' not in cache");
			if (mapStylesCollection.getStyleByName(rendName) == null) {
				Log.d(TAG, "Unknown '" + rendName + "' style, need to load");
				loadRenderer(rendName);
			}
			ResolvedMapStyle mapStyle = mapStylesCollection.getResolvedStyleByName(rendName);
			if (mapStyle != null) {
				mapStyles.put(rendName, mapStyle);
			} else {
				Log.d(TAG, "Failed to resolve '" + rendName + "', will use 'default'");
				rendName = "default";
			}
		}
		ResolvedMapStyle mapStyle = mapStyles.get(rendName);
		CachedMapPresentation pres = new CachedMapPresentation(langId, langPref, mapStyle, density,
				settings.MAP_DENSITY.get(), settings.TEXT_SCALE.get());
		if (this.presentationObjectParams == null || !this.presentationObjectParams.equalsFields(pres)) {
			this.presentationObjectParams = pres;
			mapPresentationEnvironment = new MapPresentationEnvironment(mapStyle, density,
					settings.MAP_DENSITY.get(), settings.TEXT_SCALE.get(), langId,
					langPref);
		}

		QStringStringHash convertedStyleSettings = getMapStyleSettings();
		mapPresentationEnvironment.setSettings(convertedStyleSettings);

		if ((obfMapRasterLayerProvider != null || obfMapSymbolsProvider != null) && isVectorLayerEnabled()) {
			recreateRasterAndSymbolsProvider();
			setMapBackgroundColor();
		}
		PluginsHelper.updateMapPresentationEnvironment(this);
	}

	public MapPresentationEnvironment getMapPresentationEnvironment() {
		return mapPresentationEnvironment;
	}

	private void setMapBackgroundColor() {
		RenderingRulesStorage rrs = app.getRendererRegistry().getCurrentSelectedRenderer();
		int color = MAP_DEFAULT_COLOR;
		if (rrs != null) {
			RenderingRuleSearchRequest req = new RenderingRuleSearchRequest(rrs);
			req.setBooleanFilter(rrs.PROPS.R_NIGHT_MODE, nightMode);
			if (req.searchRenderingAttribute(RenderingRuleStorageProperties.A_DEFAULT_COLOR)) {
				color = req.getIntPropertyValue(req.ALL.R_ATTR_COLOR_VALUE);
			}
		}
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView != null) {
			mapRendererView.setBackgroundColor(NativeUtilities.createFColorRGB(color));
		}
	}

	private void loadRendererAddons() {
		Map<String, String> rendererAddons = app.getRendererRegistry().getRendererAddons();
		for (Entry<String, String> addonEntry : rendererAddons.entrySet()) {
			String name = addonEntry.getKey();
			String fileName = addonEntry.getValue();
			if (mapStylesCollection.getStyleByName(fileName) == null) {
				try {
					loadStyleFromStream(fileName, app.getRendererRegistry().getInputStream(name));
				} catch (IOException e) {
					Log.e(TAG, "Failed to load '" + fileName + "'", e);
				}
			}
		}
	}

	private void loadRenderer(String rendName) {
		RenderingRulesStorage renderer = app.getRendererRegistry().getRenderer(rendName);
		if (mapStylesCollection.getStyleByName(rendName) == null && renderer != null) {
			try {
				loadStyleFromStream(rendName, app.getRendererRegistry().getInputStream(rendName));
				if (renderer.getDependsName() != null) {
					loadRenderer(renderer.getDependsName());
				}
			} catch (IOException e) {
				Log.e(TAG, "Failed to load '" + rendName + "'", e);
			}
		}
	}

	protected QStringStringHash getMapStyleSettings() {
		// Apply map style settings
		OsmandSettings settings = app.getSettings();
		RenderingRulesStorage storage = app.getRendererRegistry().getCurrentSelectedRenderer();

		Map<String, String> properties = new HashMap<>();
		for (RenderingRuleProperty property : storage.PROPS.getCustomRules()) {
			String attrName = property.getAttrName();
			if (property.isBoolean()) {
				properties.put(attrName, settings.getRenderBooleanPropertyValue(attrName) + "");
			} else {
				String value = settings.getRenderPropertyValue(attrName);
				if (!Algorithms.isEmpty(value)) {
					properties.put(attrName, value);
				}
			}
		}

		QStringStringHash convertedStyleSettings = new QStringStringHash();
		for (Entry<String, String> setting : properties.entrySet()) {
			convertedStyleSettings.set(setting.getKey(), setting.getValue());
		}
		if (nightMode) {
			convertedStyleSettings.set("nightMode", "true");
		}
		return convertedStyleSettings;
	}

	public void recreateRasterAndSymbolsProvider() {
		// Create new map primitiviser
		// TODO Victor ask MapPrimitiviser, ObfMapObjectsProvider  
		mapPrimitiviser = new MapPrimitiviser(mapPresentationEnvironment);
		ObfMapObjectsProvider obfMapObjectsProvider = new ObfMapObjectsProvider(obfsCollection);
		// Create new map primitives provider
		MapPrimitivesProvider mapPrimitivesProvider = new MapPrimitivesProvider(obfMapObjectsProvider, mapPrimitiviser,
				getRasterTileSize());
		updateObfMapRasterLayerProvider(mapPrimitivesProvider);
		updateObfMapSymbolsProvider(mapPrimitivesProvider);
	}

	public void resetRasterAndSymbolsProvider() {
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView != null) {
			mapRendererView.resetMapLayerProvider(OBF_RASTER_LAYER);
		}
		if (obfMapSymbolsProvider != null && mapRendererView != null) {
			mapRendererView.removeSymbolsProvider(obfMapSymbolsProvider);
		}
	}

	public void recreateHeightmapProvider() {
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView != null) {
			OsmandDevelopmentPlugin plugin = PluginsHelper.getPlugin(OsmandDevelopmentPlugin.class);
			if (plugin == null || !plugin.is3DMapsEnabled()) {
				mapRendererView.resetElevationDataProvider();
				return;
			}
			GeoTiffCollection geoTiffCollection = getGeoTiffCollection();
			int elevationTileSize = mapRendererView.getElevationDataTileSize();
			mapRendererView.setElevationDataProvider(new SqliteHeightmapTileProvider(geoTiffCollection, elevationTileSize));
		}
	}
	public void resetHeightmapProvider() {
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView != null) {
			mapRendererView.resetElevationDataProvider();
		}
	}

	private void updateObfMapRasterLayerProvider(MapPrimitivesProvider mapPrimitivesProvider) {
		// Create new OBF map raster layer provider
		obfMapRasterLayerProvider = new MapRasterLayerProvider_Software(mapPrimitivesProvider);
		// In case there's bound view and configured layer, perform setup
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView != null) {
			mapRendererView.setMapLayerProvider(OBF_RASTER_LAYER, obfMapRasterLayerProvider);
		}
	}

	private void updateObfMapSymbolsProvider(MapPrimitivesProvider mapPrimitivesProvider) {
		// If there's current provider and bound view, remove it
		MapRendererView mapRendererView = this.mapRendererView;
		if (obfMapSymbolsProvider != null && mapRendererView != null) {
			mapRendererView.removeSymbolsProvider(obfMapSymbolsProvider);
		}
		// Create new OBF map symbols provider
		obfMapSymbolsProvider = new MapObjectsSymbolsProvider(mapPrimitivesProvider,
				getReferenceTileSize());
		// If there's bound view, add new provider
		if (mapRendererView != null) {
			mapRendererView.addSymbolsProvider(MapRendererContext.OBF_SYMBOL_SECTION, obfMapSymbolsProvider);
		}
	}

	private void applyCurrentContextToView() {
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView == null) {
			return;
		}
		mapRendererView.setMapRendererSetupOptionsConfigurator(
				mapRendererSetupOptions -> mapRendererSetupOptions.setMaxNumberOfRasterMapLayersInBatch(1));
		if (mapRendererView instanceof AtlasMapRendererView) {
			cachedReferenceTileSize = getReferenceTileSize();
			((AtlasMapRendererView) mapRendererView).setReferenceTileSizeOnScreenInPixels(cachedReferenceTileSize);
		}
		updateElevationConfiguration();

		if (isVectorLayerEnabled()) {
			// Layers
			if (obfMapRasterLayerProvider != null) {
				mapRendererView.setMapLayerProvider(OBF_RASTER_LAYER, obfMapRasterLayerProvider);
			}
			// Symbols
			if (obfMapSymbolsProvider != null) {
				mapRendererView.addSymbolsProvider(MapRendererContext.OBF_SYMBOL_SECTION, obfMapSymbolsProvider);
			}
			// Heightmap
			recreateHeightmapProvider();
		}
	}

	public void updateElevationConfiguration() {
		MapRendererView mapRendererView = this.mapRendererView;
		if (mapRendererView == null) {
			return;
		}

		OsmandDevelopmentPlugin developmentPlugin = PluginsHelper.getEnabledPlugin(OsmandDevelopmentPlugin.class);
		ElevationConfiguration elevationConfiguration = new ElevationConfiguration();
		boolean disableVertexHillshade = developmentPlugin != null && developmentPlugin.disableVertexHillshade3D();
		if (disableVertexHillshade) {
			elevationConfiguration.setSlopeAlgorithm(SlopeAlgorithm.None);
			elevationConfiguration.setVisualizationStyle(VisualizationStyle.None);
		}

		mapRendererView.setElevationConfiguration(elevationConfiguration);
	}

	public void updateCachedHeightmapTiles() {
		GeoTiffCollection geoTiffCollection = getGeoTiffCollection();
		for (RasterType rasterType : RasterType.values()) {
			geoTiffCollection.refreshTilesInCache(rasterType);
		}
	}

	public void removeCachedHeightmapTiles(@NonNull String filePath) {
		GeoTiffCollection geoTiffCollection = getGeoTiffCollection();
		for (RasterType rasterType : RasterType.values()) {
			geoTiffCollection.removeFileTilesFromCache(rasterType, filePath);
		}
	}

	@NonNull
	public GeoTiffCollection getGeoTiffCollection() {
		if (geoTiffCollection == null) {
			geoTiffCollection = new GeoTiffCollection();
			File sqliteCacheDir = new File(app.getCacheDir(), GEOTIFF_SQLITE_CACHE_DIR);
			if (!sqliteCacheDir.exists()) {
				sqliteCacheDir.mkdir();
			}
			File geotiffDir = app.getAppPath(GEOTIFF_DIR);
			if (!geotiffDir.exists()) {
				geotiffDir.mkdir();
			}
			geoTiffCollection.addDirectory(geotiffDir.getAbsolutePath());
			geoTiffCollection.setLocalCache(sqliteCacheDir.getAbsolutePath());
		}
		return geoTiffCollection;
	}

	@Nullable
	public MapPrimitiviser getMapPrimitiviser() {
		return mapPrimitiviser;
	}


	private static class CachedMapPresentation {
		String langId;
		LanguagePreference langPref;
		ResolvedMapStyle mapStyle;
		float displayDensityFactor;
		float mapScaleFactor;
		float symbolsScaleFactor;

		public CachedMapPresentation(String langId,
		                             LanguagePreference langPref, ResolvedMapStyle mapStyle,
		                             float displayDensityFactor,
		                             float mapScaleFactor,
		                             float symbolsScaleFactor) {
			this.langId = langId;
			this.langPref = langPref;
			this.mapStyle = mapStyle;
			this.displayDensityFactor = displayDensityFactor;
			this.mapScaleFactor = mapScaleFactor;
			this.symbolsScaleFactor = symbolsScaleFactor;
		}


		public boolean equalsFields(CachedMapPresentation other) {
			if (Double.compare(displayDensityFactor, other.displayDensityFactor) != 0)
				return false;
			if (Double.compare(mapScaleFactor, other.mapScaleFactor) != 0)
				return false;
			if (Double.compare(symbolsScaleFactor, other.symbolsScaleFactor) != 0)
				return false;
			if (langId == null) {
				if (other.langId != null)
					return false;
			} else if (!langId.equals(other.langId))
				return false;
			if (langPref != other.langPref)
				return false;
			if (mapStyle == null) {
				return other.mapStyle == null;
			} else return mapStyle.equals(other.mapStyle);
		}
	}

	private void loadStyleFromStream(String name, InputStream source) {
		if (source == null) {
			return;
		}
		if (RendererRegistry.DEFAULT_RENDER.equals(name)) {
			Algorithms.closeStream(source);
			return;
		}

		Log.d(TAG, "Going to pass '" + name + "' style content to native");
		byte[] content;
		try {
			ByteArrayOutputStream intermediateBuffer = new ByteArrayOutputStream();
			int nRead;
			byte[] data = new byte[16384];
			while ((nRead = source.read(data, 0, data.length)) != -1) {
				intermediateBuffer.write(data, 0, nRead);
			}
			intermediateBuffer.flush();
			content = intermediateBuffer.toByteArray();
		} catch (IOException e) {
			Log.e(TAG, "Failed to read style content", e);
			return;
		} finally {
			Algorithms.closeStream(source);
		}

		if (!mapStylesCollection.addStyleFromByteArray(
				SwigUtilities.createQByteArrayAsCopyOf(content), name)) {
			Log.w(TAG, "Failed to add style from byte array");
		}
	}
}
