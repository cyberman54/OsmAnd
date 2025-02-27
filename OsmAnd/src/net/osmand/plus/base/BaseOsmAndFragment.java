package net.osmand.plus.base;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.animation.Animation;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.Dimension;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.OsmandActionBarActivity;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.UiUtilities;

public class BaseOsmAndFragment extends Fragment implements TransitionAnimator {

	protected OsmandApplication app;
	protected OsmandSettings settings;
	protected UiUtilities uiUtilities;
	protected boolean nightMode;

	private int statusBarColor = -1;
	private boolean transitionAnimationAllowed = true;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = (OsmandApplication) requireActivity().getApplication();
		settings = app.getSettings();
		uiUtilities = app.getUIUtilities();
		nightMode = isNightMode(isUsedOnMap());
	}

	protected boolean isUsedOnMap() {
		return false;
	}

	@Override
	public void onResume() {
		super.onResume();
		Activity activity = getActivity();
		if (activity != null) {
			int colorId = getStatusBarColorId();
			if (colorId != -1) {
				if (activity instanceof MapActivity) {
					((MapActivity) activity).updateStatusBarColor();
				} else {
					statusBarColor = activity.getWindow().getStatusBarColor();
					activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, colorId));
				}
			}
			if (!isFullScreenAllowed() && activity instanceof MapActivity) {
				((MapActivity) activity).exitFromFullScreen(getView());
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Activity activity = getActivity();
		if (activity != null) {
			if (!(activity instanceof MapActivity) && statusBarColor != -1) {
				activity.getWindow().setStatusBarColor(statusBarColor);
			}
			if (!isFullScreenAllowed() && activity instanceof MapActivity) {
				((MapActivity) activity).enterToFullScreen();
			}
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		if (getStatusBarColorId() != -1) {
			Activity activity = getActivity();
			if (activity instanceof MapActivity) {
				((MapActivity) activity).updateStatusBarColor();
			}
		}
	}

	@Override
	public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
		if (transitionAnimationAllowed) {
			return super.onCreateAnimation(transit, enter, nextAnim);
		}
		Animation anim = new Animation() {
		};
		anim.setDuration(0);
		return anim;
	}

	@Override
	public void disableTransitionAnimation() {
		transitionAnimationAllowed = false;
	}

	@Override
	public void enableTransitionAnimation() {
		transitionAnimationAllowed = true;
	}

	@ColorRes
	public int getStatusBarColorId() {
		return -1;
	}

	protected boolean isFullScreenAllowed() {
		return true;
	}

	@Nullable
	protected OsmandActionBarActivity getMyActivity() {
		return (OsmandActionBarActivity) getActivity();
	}

	@NonNull
	protected OsmandActionBarActivity requireMyActivity() {
		return (OsmandActionBarActivity) requireActivity();
	}

	protected Drawable getPaintedContentIcon(@DrawableRes int id, @ColorInt int color) {
		return uiUtilities.getPaintedIcon(id, color);
	}

	protected Drawable getIcon(@DrawableRes int id) {
		return uiUtilities.getIcon(id);
	}

	protected Drawable getIcon(@DrawableRes int id, @ColorRes int colorId) {
		return uiUtilities.getIcon(id, colorId);
	}

	protected Drawable getContentIcon(@DrawableRes int id) {
		return uiUtilities.getThemedIcon(id);
	}

	@ColorInt
	protected int getColor(@ColorRes int resId) {
		return ColorUtilities.getColor(app, resId);
	}

	@Dimension
	protected int getDimensionPixelSize(@DimenRes int resId) {
		return getResources().getDimensionPixelSize(resId);
	}

	protected boolean isNightMode(boolean usedOnMap) {
		return app.getDaynightHelper().isNightMode(usedOnMap);
	}
}
