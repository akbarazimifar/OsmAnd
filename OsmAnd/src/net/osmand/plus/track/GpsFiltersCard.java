package net.osmand.plus.track;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import net.osmand.plus.ColorUtilities;
import net.osmand.plus.FilteredSelectedGpxFile;
import net.osmand.plus.GPXDatabase.GpxDataItem;
import net.osmand.plus.GpxDbHelper;
import net.osmand.plus.GpxDbHelper.GpxDataItemCallback;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.GpsFilterHelper.GpsFilter;

import java.io.File;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;

public class GpsFiltersCard extends GpsFilterBaseCard {

	private View view;

	private final GpxDbHelper gpxDbHelper;
	private GpxDataItem gpxDataItem;

	public GpsFiltersCard(@NonNull MapActivity mapActivity,
	                      @NonNull Fragment target,
	                      @NonNull FilteredSelectedGpxFile filteredSelectedGpxFile) {
		super(mapActivity, target, filteredSelectedGpxFile);
		gpxDbHelper = app.getGpxDbHelper();
		gpxDataItem = fetchGpxDataItem();
	}

	@Nullable
	private GpxDataItem fetchGpxDataItem() {
		File file = new File(filteredSelectedGpxFile.getGpxFile().path);
		GpxDataItemCallback callback = new GpxDataItemCallback() {
			@Override
			public boolean isCancelled() {
				return false;
			}

			@Override
			public void onGpxDataItemReady(GpxDataItem item) {
				if (item != null) {
					gpxDataItem = item;
				}
			}
		};
		return gpxDbHelper.getItem(file, callback);
	}

	@Override
	protected int getMainContentLayoutId() {
		return R.layout.gps_filters_list;
	}

	@Override
	protected void updateMainContent() {
		if (view == null) {
			view = inflateMainContent();
		}

		updatePointsRatio();
		setupSmoothingFilter();
		setupSpeedFilter();
		setupAltitudeFilter();
		setupHdopFilter();
	}

	private void updatePointsRatio() {
		String pointsString = app.getString(R.string.shared_string_gpx_points);
		String leftPoints = String.valueOf(filteredSelectedGpxFile.getLeftPointsCount());
		String totalPoints = String.valueOf(filteredSelectedGpxFile.getTotalPointsCount());
		String ratio = app.getString(R.string.ltr_or_rtl_combine_via_slash_with_space, leftPoints, totalPoints);
		String fullText = app.getString(R.string.ltr_or_rtl_combine_via_colon, pointsString, ratio);
		SpannableString spannedText = new SpannableString(fullText);
		spannedText.setSpan(new StyleSpan(Typeface.BOLD), 0, pointsString.length() + 1,
				Spanned.SPAN_EXCLUSIVE_INCLUSIVE);

		TextView pointsRatio = view.findViewById(R.id.points_ratio);
		pointsRatio.setText(spannedText);
	}

	private void setupSmoothingFilter() {
		setupFilter(view.findViewById(R.id.smoothing_filter), filteredSelectedGpxFile.getSmoothingFilter());
	}

	private void setupSpeedFilter() {
		setupFilter(view.findViewById(R.id.speed_filter), filteredSelectedGpxFile.getSpeedFilter());
	}

	private void setupAltitudeFilter() {
		setupFilter(view.findViewById(R.id.altitude_filter), filteredSelectedGpxFile.getAltitudeFilter());
	}

	private void setupHdopFilter() {
		setupFilter(view.findViewById(R.id.hdop_filter), filteredSelectedGpxFile.getHdopFilter());
	}

	private void setupFilter(@NonNull View container, @NonNull GpsFilter filter) {
		View header = container.findViewById(R.id.filter_header);
		View content = container.findViewById(R.id.filter_content);
		AppCompatImageView upDownButton = container.findViewById(R.id.up_down_button);

		if (!filter.isNeeded()) {
			updateDisplayedFilterNumbers(container, filter);
			updateUpDownButton(upDownButton, false);
			AndroidUiHelper.updateVisibility(content, false);
			return;
		} else {
			updateUpDownButton(upDownButton, content.getVisibility() == View.VISIBLE);
		}

		header.setOnClickListener(v -> {
			boolean expanded = content.getVisibility() == View.VISIBLE;
			updateUpDownButton(upDownButton, !expanded);
			AndroidUiHelper.updateVisibility(content, !expanded);
		});

		setupSlider(container, filter);
		updateDisplayedFilterNumbers(container, filter);

		TextView minFilterValue = container.findViewById(R.id.min_filter_value);
		minFilterValue.setText(filter.getFormattedStyledValue(app, filter.getMinValue()));

		TextView maxFilterValue = container.findViewById(R.id.max_filter_value);
		maxFilterValue.setText(filter.getFormattedStyledValue(app, filter.getMaxValue()));

		TextView filterDescription = container.findViewById(R.id.filter_description);
		filterDescription.setText(filter.getDescriptionId());
	}

	private void updateUpDownButton(@NonNull AppCompatImageView upDownButton, boolean up) {
		int upDownIconId = up ? R.drawable.ic_action_arrow_up : R.drawable.ic_action_arrow_down;
		Drawable upDownIcon = getColoredIcon(upDownIconId, ColorUtilities.getDefaultIconColorId(nightMode));
		upDownButton.setImageDrawable(upDownIcon);
	}

	private void setupSlider(@NonNull final View container, @NonNull final GpsFilter filter) {
		boolean range = filter.isRangeSupported();
		boolean enabled = filter.getMinValue() != filter.getMaxValue();
		Slider slider = container.findViewById(R.id.filter_slider);
		RangeSlider rangeSlider = container.findViewById(R.id.filter_range_slider);

		AndroidUiHelper.updateVisibility(slider, !range);
		AndroidUiHelper.updateVisibility(rangeSlider, range);

		if (range) {
			rangeSlider.setEnabled(enabled);
			rangeSlider.clearOnChangeListeners();
			if (enabled) {
				rangeSlider.setValueFrom((float) filter.getMinValue());
				rangeSlider.setValueTo((float) filter.getMaxValue());
				rangeSlider.setValues(((float) filter.getSelectedMinValue()), ((float) filter.getSelectedMaxValue()));
				rangeSlider.addOnChangeListener((slider1, value, fromUser) -> {
					List<Float> values = rangeSlider.getValues();
					if (fromUser && values.size() == 2) {
						filter.updateValues((values.get(0)), values.get(1));
						updateDisplayedFilterNumbers(container, filter);
						if (gpxDataItem != null) {
							boolean updated = gpxDbHelper.updateGpsFilters(gpxDataItem, filteredSelectedGpxFile);
							if (updated) {
								gpsFilterHelper.filterGpxFile(filteredSelectedGpxFile, true);
							}
						}
					}
				});
			}
			UiUtilities.setupSlider(rangeSlider, nightMode, ColorUtilities.getActiveColor(app, nightMode), false);
		} else {
			slider.setEnabled(enabled);
			slider.clearOnChangeListeners();
			if (enabled) {
				slider.setValueFrom((float) filter.getMinValue());
				slider.setValueTo((float) filter.getMaxValue());
				slider.setValue((float) filter.getSelectedMaxValue());
				slider.addOnChangeListener((slider1, value, fromUser) -> {
					if (fromUser) {
						filter.updateValue((slider.getValue()));
						updateDisplayedFilterNumbers(container, filter);
						if (gpxDataItem != null) {
							boolean updated = gpxDbHelper.updateGpsFilters(gpxDataItem, filteredSelectedGpxFile);
							if (updated) {
								gpsFilterHelper.filterGpxFile(filteredSelectedGpxFile, true);
							}
						}
					}
				});
			}
			UiUtilities.setupSlider(slider, nightMode, ColorUtilities.getActiveColor(app, nightMode));
		}
	}

	private void updateDisplayedFilterNumbers(@NonNull View container, @NonNull GpsFilter filter) {
		TextView title = container.findViewById(R.id.filter_title);
		title.setText(filter.getFilterTitle(app));

		TextView leftText = container.findViewById(R.id.left_text);
		leftText.setText(filter.getLeftText(app));

		TextView rightText = container.findViewById(R.id.right_text);
		rightText.setText(filter.getRightText(app));
	}

	@Override
	public void onFinishFiltering() {
		updatePointsRatio();
	}
}