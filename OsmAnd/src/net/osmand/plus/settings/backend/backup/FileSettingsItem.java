package net.osmand.plus.settings.backend.backup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.download.SrtmDownloadItem;
import net.osmand.util.Algorithms;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileSettingsItem extends StreamSettingsItem {

	public enum FileSubtype {
		UNKNOWN("", null, R.drawable.ic_type_file),
		OTHER("other", "", R.drawable.ic_type_file),
		ROUTING_CONFIG("routing_config", IndexConstants.ROUTING_PROFILES_DIR, R.drawable.ic_action_route_distance),
		RENDERING_STYLE("rendering_style", IndexConstants.RENDERERS_DIR, R.drawable.ic_action_map_style),
		WIKI_MAP("wiki_map", IndexConstants.WIKI_INDEX_DIR, R.drawable.ic_plugin_wikipedia),
		SRTM_MAP("srtm_map", IndexConstants.SRTM_INDEX_DIR, R.drawable.ic_plugin_srtm),
		OBF_MAP("obf_map", IndexConstants.MAPS_PATH, R.drawable.ic_map),
		TILES_MAP("tiles_map", IndexConstants.TILES_INDEX_DIR, R.drawable.ic_map),
		ROAD_MAP("road_map", IndexConstants.ROADS_INDEX_DIR, R.drawable.ic_map),
		GPX("gpx", IndexConstants.GPX_INDEX_DIR, R.drawable.ic_action_route_distance),
		TTS_VOICE("tts_voice", IndexConstants.VOICE_INDEX_DIR, R.drawable.ic_action_volume_up),
		VOICE("voice", IndexConstants.VOICE_INDEX_DIR, R.drawable.ic_action_volume_up),
		TRAVEL("travel", IndexConstants.WIKIVOYAGE_INDEX_DIR, R.drawable.ic_plugin_wikipedia),
		MULTIMEDIA_NOTES("multimedia_notes", IndexConstants.AV_INDEX_DIR, R.drawable.ic_action_photo_dark);

		private final String subtypeName;
		private final String subtypeFolder;
		private final int iconId;

		FileSubtype(@NonNull String subtypeName, String subtypeFolder, @DrawableRes int iconId) {
			this.subtypeName = subtypeName;
			this.subtypeFolder = subtypeFolder;
			this.iconId = iconId;
		}

		public boolean isMap() {
			return this == OBF_MAP || this == WIKI_MAP || this == SRTM_MAP || this == TILES_MAP || this == ROAD_MAP;
		}

		public String getSubtypeName() {
			return subtypeName;
		}

		public String getSubtypeFolder() {
			return subtypeFolder;
		}

		@DrawableRes
		public int getIconId() {
			return iconId;
		}

		public static FileSubtype getSubtypeByName(@NonNull String name) {
			for (FileSubtype subtype : FileSubtype.values()) {
				if (name.equals(subtype.subtypeName)) {
					return subtype;
				}
			}
			return null;
		}

		public static FileSubtype getSubtypeByPath(@NonNull OsmandApplication app, @NonNull String fileName) {
			fileName = fileName.replace(app.getAppPath(null).getPath(), "");
			return getSubtypeByFileName(fileName);
		}

		public static FileSubtype getSubtypeByFileName(@NonNull String fileName) {
			String name = fileName;
			if (fileName.startsWith(File.separator)) {
				name = fileName.substring(1);
			}
			for (FileSubtype subtype : FileSubtype.values()) {
				switch (subtype) {
					case UNKNOWN:
					case OTHER:
						break;
					case SRTM_MAP:
						if (SrtmDownloadItem.isSrtmFile(name)) {
							return subtype;
						}
						break;
					case WIKI_MAP:
						if (name.endsWith(IndexConstants.BINARY_WIKI_MAP_INDEX_EXT)) {
							return subtype;
						}
						break;
					case OBF_MAP:
						if (name.endsWith(IndexConstants.BINARY_MAP_INDEX_EXT) && !name.contains(File.separator)) {
							return subtype;
						}
						break;
					case TTS_VOICE:
						if (name.startsWith(subtype.subtypeFolder) && name.endsWith(IndexConstants.VOICE_PROVIDER_SUFFIX)) {
							return subtype;
						}
						break;
					default:
						if (name.startsWith(subtype.subtypeFolder)) {
							return subtype;
						}
						break;
				}
			}
			return UNKNOWN;
		}

		@NonNull
		@Override
		public String toString() {
			return subtypeName;
		}
	}

	protected File file;
	protected File savedFile;
	private final File appPath;
	protected FileSubtype subtype;
	private long size;
	private long lastModified;

	public FileSettingsItem(@NonNull OsmandApplication app, @NonNull File file) throws IllegalArgumentException {
		super(app, file.getPath().replace(app.getAppPath(null).getPath(), ""));
		this.file = file;
		this.appPath = app.getAppPath(null);
		String fileName = getFileName();
		if (fileName != null) {
			this.subtype = FileSubtype.getSubtypeByFileName(fileName);
		}
		if (subtype == FileSubtype.UNKNOWN || subtype == null) {
			throw new IllegalArgumentException("Unknown file subtype: " + fileName);
		}
	}

	FileSettingsItem(@NonNull OsmandApplication app, @NonNull JSONObject json) throws JSONException {
		super(app, json);
		this.appPath = app.getAppPath(null);
		if (subtype == FileSubtype.OTHER) {
			this.file = new File(appPath, name);
		} else if (subtype == FileSubtype.UNKNOWN || subtype == null) {
			throw new IllegalArgumentException("Unknown file subtype: " + getFileName());
		} else {
			String subtypeFolder = subtype.subtypeFolder;
			int nameIndex = fileName.indexOf(name);
			int folderIndex = fileName.indexOf(subtype.subtypeFolder);
			if (nameIndex != -1 && folderIndex != -1) {
				String subfolderPath = fileName.substring(folderIndex + subtype.subtypeFolder.length(), nameIndex);
				subtypeFolder = subtypeFolder + subfolderPath;
			}
			this.file = new File(app.getAppPath(subtypeFolder), name);
		}
	}

	@NonNull
	@Override
	public SettingsItemType getType() {
		return SettingsItemType.FILE;
	}

	public File getPluginPath() {
		String pluginId = getPluginId();
		if (!Algorithms.isEmpty(pluginId)) {
			return new File(appPath, IndexConstants.PLUGINS_DIR + pluginId);
		}
		return appPath;
	}

	@Override
	void readFromJson(@NonNull JSONObject json) throws JSONException {
		super.readFromJson(json);
		String fileName = getFileName();
		if (subtype == null) {
			String subtypeStr = json.has("subtype") ? json.getString("subtype") : null;
			if (!Algorithms.isEmpty(subtypeStr)) {
				subtype = FileSubtype.getSubtypeByName(subtypeStr);
			} else if (!Algorithms.isEmpty(fileName)) {
				subtype = FileSubtype.getSubtypeByFileName(fileName);
			} else {
				subtype = FileSubtype.UNKNOWN;
			}
		}
		if (!Algorithms.isEmpty(fileName)) {
			if (subtype == FileSubtype.OTHER) {
				name = fileName;
			} else if (subtype != null && subtype != FileSubtype.UNKNOWN) {
				name = Algorithms.getFileWithoutDirs(fileName);
			}
		}
	}

	@Override
	void writeToJson(@NonNull JSONObject json) throws JSONException {
		super.writeToJson(json);
		if (subtype != null) {
			json.put("subtype", subtype.getSubtypeName());
		}
	}

	public long getSize() {
		if (size != 0) {
			return size;
		} else if (file != null && !file.isDirectory()) {
			return file.length();
		}
		return 0;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public long getLastModified() {
		return lastModified;
	}

	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}

	@NonNull
	public File getFile() {
		return file;
	}

	@NonNull
	public FileSubtype getSubtype() {
		return subtype;
	}

	@Override
	public boolean exists() {
		return file.exists();
	}

	private File renameFile(File oldFile) {
		String oldPath = oldFile.getAbsolutePath();
		String prefix;
		if (file.isDirectory()) {
			prefix = file.getAbsolutePath();
		} else if (oldPath.endsWith(IndexConstants.BINARY_WIKI_MAP_INDEX_EXT)) {
			prefix = oldPath.substring(0, oldPath.lastIndexOf(IndexConstants.BINARY_WIKI_MAP_INDEX_EXT));
		} else if (oldPath.endsWith(IndexConstants.BINARY_SRTM_MAP_INDEX_EXT)) {
			prefix = oldPath.substring(0, oldPath.lastIndexOf(IndexConstants.BINARY_SRTM_MAP_INDEX_EXT));
		} else if (oldPath.endsWith(IndexConstants.BINARY_SRTM_FEET_MAP_INDEX_EXT)) {
			prefix = oldPath.substring(0, oldPath.lastIndexOf(IndexConstants.BINARY_SRTM_FEET_MAP_INDEX_EXT));
		} else if (oldPath.endsWith(IndexConstants.BINARY_ROAD_MAP_INDEX_EXT)) {
			prefix = oldPath.substring(0, oldPath.lastIndexOf(IndexConstants.BINARY_ROAD_MAP_INDEX_EXT));
		} else {
			prefix = oldPath.substring(0, oldPath.lastIndexOf("."));
		}
		String suffix = oldPath.replace(prefix, "");
		int number = 0;
		while (true) {
			number++;
			String newName = prefix + "_" + number + suffix;
			File newFile = new File(newName);
			if (!newFile.exists()) {
				return newFile;
			}
		}
	}

	@Nullable
	@Override
	SettingsItemReader<? extends SettingsItem> getReader() {
		return new StreamSettingsItemReader(this) {
			@Override
			public void readFromStream(@NonNull InputStream inputStream, String entryName) throws IOException, IllegalArgumentException {
				OutputStream output;
				savedFile = FileSettingsItem.this.getFile();
				if (savedFile.isDirectory()) {
					savedFile = new File(savedFile, entryName.substring(fileName.length()));
				}
				if (savedFile.exists() && !shouldReplace) {
					savedFile = renameFile(savedFile);
				}
				if (savedFile.getParentFile() != null && !savedFile.getParentFile().exists()) {
					//noinspection ResultOfMethodCallIgnored
					savedFile.getParentFile().mkdirs();
				}
				output = new FileOutputStream(savedFile);
				byte[] buffer = new byte[SettingsHelper.BUFFER];
				int count;
				try {
					while ((count = inputStream.read(buffer)) != -1) {
						output.write(buffer, 0, count);
					}
					output.flush();
				} finally {
					Algorithms.closeStream(output);
				}
				if (lastModified != -1) {
					savedFile.setLastModified(lastModified);
				}
			}
		};
	}

	@Nullable
	@Override
	public SettingsItemWriter<? extends SettingsItem> getWriter() {
		if (!file.isDirectory()) {
			try {
				setInputStream(PlatformUtil.getFileInputStream(file));
			} catch (FileNotFoundException e) {
				warnings.add(app.getString(R.string.settings_item_read_error, file.getName()));
				SettingsHelper.LOG.error("Failed to set input stream from file: " + file.getName(), e);
			}
			return new StreamSettingsItemWriter(this) {
				@Override
				public ZipEntry createNewEntry(String fileName) {
					ZipEntry entry = super.createNewEntry(fileName);
					entry.setTime(file.lastModified());
					return entry;
				}
			};
		} else {
			return new StreamSettingsItemWriter(this) {

				@Override
				public void writeEntry(String fileName, @NonNull ZipOutputStream zos) throws IOException {
					writeDirWithFiles(file, zos);
				}

				public void writeDirWithFiles(File file, ZipOutputStream zos) throws IOException {
					if (file != null) {
						if (file.isDirectory()) {
							File[] files = file.listFiles();
							if (files != null) {
								for (File subfolderFile : files) {
									writeDirWithFiles(subfolderFile, zos);
								}
							}
						} else {
							String subtypeFolder = getSubtype().getSubtypeFolder();
							String zipEntryName = Algorithms.isEmpty(subtypeFolder)
									? file.getName()
									: file.getPath().substring(file.getPath().indexOf(subtypeFolder) - 1);
							setInputStream(PlatformUtil.getFileInputStream(file));
							super.writeEntry(zipEntryName, zos);
						}
					}
				}
			};
		}
	}
}
