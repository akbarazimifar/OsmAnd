package net.osmand.plus.mapcontextmenu.controllers;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.osmand.data.Amenity;
import net.osmand.data.FavouritePoint;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.TransportStop;
import net.osmand.plus.FavouritesDbHelper;
import net.osmand.plus.MapMarkersHelper;
import net.osmand.plus.MapMarkersHelper.MapMarker;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.PointImageDrawable;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.mapcontextmenu.MenuController;
import net.osmand.plus.mapcontextmenu.builders.FavouritePointMenuBuilder;
import net.osmand.plus.mapcontextmenu.editors.FavoritePointEditor;
import net.osmand.plus.mapcontextmenu.editors.FavoritePointEditorFragment;
import net.osmand.plus.mapcontextmenu.editors.FavoritePointEditorFragmentNew;
import net.osmand.plus.transport.TransportStopRoute;
import net.osmand.plus.widgets.style.CustomTypefaceSpan;
import net.osmand.util.OpeningHoursParser;
import net.osmand.view.GravityDrawable;

import java.util.List;

public class FavouritePointMenuController extends MenuController {

	private FavouritePoint fav;
	private MapMarker mapMarker;

	private TransportStopController transportStopController;

	public FavouritePointMenuController(@NonNull MapActivity mapActivity, @NonNull PointDescription pointDescription, final @NonNull FavouritePoint fav) {
		super(new FavouritePointMenuBuilder(mapActivity, fav), pointDescription, mapActivity);
		this.fav = fav;

		final MapMarkersHelper markersHelper = mapActivity.getMyApplication().getMapMarkersHelper();
		mapMarker = markersHelper.getMapMarker(fav);
		if (mapMarker == null) {
			mapMarker = markersHelper.getMapMarker(new LatLon(fav.getLatitude(), fav.getLongitude()));
		}
		if (mapMarker != null) {
			MapMarkerMenuController markerMenuController =
					new MapMarkerMenuController(mapActivity, mapMarker.getPointDescription(mapActivity), mapMarker);
			leftTitleButtonController = markerMenuController.getLeftTitleButtonController();
			rightTitleButtonController = markerMenuController.getRightTitleButtonController();
		}
		if (getObject() instanceof TransportStop) {
			TransportStop stop = (TransportStop) getObject();
			transportStopController = new TransportStopController(mapActivity, pointDescription, stop);
			transportStopController.processRoutes();
		}

		Object originObject = getBuilder().getOriginObject();
		if (originObject instanceof Amenity) {
			openingHoursInfo = OpeningHoursParser.getInfo(((Amenity) originObject).getOpeningHours());
		}
	}

	@Override
	protected void setObject(Object object) {
		if (object instanceof FavouritePoint) {
			this.fav = (FavouritePoint) object;
		}
	}

	@Override
	protected Object getObject() {
		return fav;
	}

	@Override
	public List<TransportStopRoute> getTransportStopRoutes() {
		if (transportStopController != null) {
			return transportStopController.getTransportStopRoutes();
		}
		return null;
	}

	@Override
	protected List<TransportStopRoute> getSubTransportStopRoutes(boolean nearby) {
		if (transportStopController != null) {
			return transportStopController.getSubTransportStopRoutes(nearby);
		}
		return null;
	}

	@Override
	public boolean handleSingleTapOnMap() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			Fragment fragment = mapActivity.getSupportFragmentManager().findFragmentByTag(FavoritePointEditor.TAG);
			if (fragment != null) {
				// TODO: uncomment & delete if else after switch to new UI Fragment
				//((FavoritePointEditorFragment) fragment).dismiss();
				if (fragment instanceof FavoritePointEditorFragmentNew) {
					((FavoritePointEditorFragmentNew) fragment).dismiss();
				} else {
					((FavoritePointEditorFragment) fragment).dismiss();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean needStreetName() {
		return false;
	}

	@Override
	public boolean needTypeStr() {
		return true;
	}

	@Override
	public boolean displayDistanceDirection() {
		return true;
	}

	@Override
	public Drawable getRightIcon() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			return PointImageDrawable.getFromFavorite(mapActivity.getMyApplication(),
					mapActivity.getMyApplication().getFavorites().getColorWithCategory(fav,
							ContextCompat.getColor(mapActivity, R.color.color_favorite)), false, fav);
		} else {
			return null;
		}
	}

	@Override
	public boolean isWaypointButtonEnabled() {
		return mapMarker == null;
	}

	@NonNull
	@Override
	public String getNameStr() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			return fav.getDisplayName(mapActivity);
		} else {
			return super.getNameStr();
		}
	}

	@NonNull
	@Override
	public CharSequence getSubtypeStr() {
		Typeface typeface = FontCache.getRobotoRegular(getMapActivity());
		SpannableString addressSpannable = new SpannableString(fav.getAddress());
		addressSpannable.setSpan(new CustomTypefaceSpan(typeface), 0, addressSpannable.length(), 0);

		return addressSpannable;
	}

	@Override
	public Drawable getSecondLineTypeIcon() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			OsmandApplication app = mapActivity.getMyApplication();
			FavouritesDbHelper helper = app.getFavorites();
			String group = fav.getCategory();
			if (helper.getGroup(group) != null) {
				Drawable line2icon = helper.getColoredIconForGroup(group);
				GravityDrawable gravityIcon = new GravityDrawable(line2icon);
				gravityIcon.setBoundsFrom(line2icon);
				return gravityIcon;
			} else {
				int colorId = isLight() ? R.color.icon_color_default_light : R.color.ctx_menu_bottom_view_icon_dark;
				return getIcon(R.drawable.ic_action_group_name_16, colorId);
			}
		}
		return null;
	}

	@Override
	public int getFavActionIconId() {
		return R.drawable.ic_action_edit_dark;
	}

	@Override
	public int getFavActionStringId() {
		return R.string.shared_string_edit;
	}

	@Override
	public boolean isFavButtonEnabled() {
		return !fav.isSpecialPoint();
	}

	@NonNull
	@Override
	public String getTypeStr() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			return fav.getCategory().length() == 0 ?
					mapActivity.getString(R.string.shared_string_favorites) : fav.getCategoryDisplayName(mapActivity);
		} else {
			return "";
		}
	}

	private FavouritePointMenuBuilder getBuilder() {
		return (FavouritePointMenuBuilder) builder;
	}

	@Override
	public void addPlainMenuItems(String typeStr, PointDescription pointDescription, LatLon latLon) {
		Object originObject = getBuilder().getOriginObject();
		if (originObject != null) {
			if (originObject instanceof Amenity) {
				AmenityMenuController.addTypeMenuItem((Amenity) originObject, builder);
			}
		}
	}
}
