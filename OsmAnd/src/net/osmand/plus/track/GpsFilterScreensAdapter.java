package net.osmand.plus.track;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.osmand.plus.ColorUtilities;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.views.controls.PagerSlidingTabStrip.CustomTabProvider;
import net.osmand.plus.views.controls.WrapContentHeightViewPager.ViewAtPositionInterface;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.PagerAdapter;

public class GpsFilterScreensAdapter extends PagerAdapter implements CustomTabProvider,
		ViewAtPositionInterface {

	private static final int SCREENS_NUMBER = 2;

	private final OsmandApplication app;
	private final MapActivity mapActivity;
	private final Fragment target;
	private final boolean nightMode;

	@ColorInt
	private final int selectedTextColor;
	@ColorInt
	private final int unselectedTextColor;

	private final List<GpsFilterBaseCard> cards = new ArrayList<>(2);
	private int currentPosition;

	public GpsFilterScreensAdapter(MapActivity mapActivity, Fragment target, boolean nightMode) {
		this.app = mapActivity.getMyApplication();
		this.mapActivity = mapActivity;
		this.target = target;
		this.nightMode = nightMode;

		this.selectedTextColor = ColorUtilities.getPrimaryTextColor(mapActivity, nightMode);
		this.unselectedTextColor = ColorUtilities.getActiveColor(mapActivity, nightMode);
	}

	public void softScrollToActionsCard() {
		cards.get(currentPosition).softScrollToActionCard();
	}

	@Override
	public int getCount() {
		return SCREENS_NUMBER;
	}

	@Override
	public View getCustomTabView(@NonNull ViewGroup parent, int position) {
		int layoutId = position == 0 ? R.layout.left_button_container : R.layout.right_button_container;
		ViewGroup tab = (ViewGroup) UiUtilities.getInflater(parent.getContext(), nightMode)
				.inflate(layoutId, parent, false);
		tab.setTag(position);
		TextView title = (TextView) tab.getChildAt(0);
		if (title != null) {
			title.setText(getPageTitle(position));
			title.setBackground(null);
			title.setAllCaps(true);
		}
		return tab;
	}

	@Nullable
	@Override
	public CharSequence getPageTitle(int position) {
		int titleId = position == 0 ? R.string.search_poi_filter : R.string.shared_string_statistic;
		return app.getString(titleId);
	}

	@Override
	public void select(View tab) {
		View parent = (View) tab.getParent();
		currentPosition = (int) tab.getTag();
		updateCustomRadioButtons(parent, currentPosition);
	}

	@Override
	public void deselect(View tab) {
	}

	@Override
	public void tabStylesUpdated(View tabsContainer, int currentPosition) {
		updateCustomRadioButtons(tabsContainer, currentPosition);
	}

	private void updateCustomRadioButtons(View buttonsView, int position) {
		TextView leftButton = buttonsView.findViewById(R.id.left_button);
		TextView rightButton = buttonsView.findViewById(R.id.right_button);
		leftButton.setTextColor(position == 0 ? selectedTextColor : unselectedTextColor);
		rightButton.setTextColor(position == 1 ? selectedTextColor : unselectedTextColor);
	}

	@NonNull
	@Override
	public Object instantiateItem(@NonNull ViewGroup container, int position) {
		GpsFilterBaseCard card = position == 0
				? new GpsFiltersCard(mapActivity, target)
				: new GpsFilterGraphCard(mapActivity, target);
		cards.add(card);
		View cardView = card.build(mapActivity);
		container.addView(cardView);
		return cardView;
	}

	@Override
	public View getViewAtPosition(int position) {
		return 0 <= position && position < SCREENS_NUMBER
				? cards.get(position).getView()
				: null;
	}

	@Override
	public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
		return view.equals(object);
	}

	@Override
	public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object item) {
		cards.remove(position);
		container.removeView(((View) item));
	}
}