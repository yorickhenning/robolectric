package org.robolectric;

import org.robolectric.annotation.Config;
import org.robolectric.internal.SdkConfig;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.FsFile;
import org.robolectric.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * A factory that detects what build system is in use and provides a ManifestFactory that can
 * create an AndroidManifest for that environment.
 *
 * <p>The following build systems are currently supported:
 * <ul>
 *   <li>Maven</li>
 *   <li>Gradle</li>
 * </ul>
 */
public abstract class ManifestFactory {
  protected static final String DEFAULT_MANIFEST_NAME = "AndroidManifest.xml";
  private static final Map<ManifestIdentifier, AndroidManifest> appManifestsByFile = new HashMap<>();

  final Config config;

  protected ManifestFactory(Config config) {
    this.config = config;
  }

  /**
   * Detects what build system is in use and returns the appropriate ManifestFactory implementation.
   * @param config Specification of the SDK version, manifest file, package name, etc.
   */
  public static ManifestFactory newManifestFactory(Config config) {
    if (config.constants() != null && config.constants() != Void.class) {
      return new GradleManifestFactory(config);
    } else {
      return new MavenManifestFactory(config);
    }
  }

  /**
   * @return A new AndroidManifest including the location of libraries, assets, resources, etc.
   */
  public abstract AndroidManifest create();

  /**
   * Search through an AndroidManifest's library directories to load library AppManifest files.
   * For testing, allow a parameter override of the library directories.
   * @param androidManifest The main AppManifest which may have library resources.
   * @param libraryDirectories If not null, override the libraries in androidManifest.
   * @return A list of AndroidManifest objects, one for each library found.
   */
  private static List<AndroidManifest> createLibraryManifests(
      AndroidManifest androidManifest,
      List<FsFile> libraryDirectories) {
    List<AndroidManifest> libraryManifests = new ArrayList<>();
    if (androidManifest != null) {
      // If there is no library override, look through subdirectories.
      if (libraryDirectories == null) {
        libraryDirectories = findLibraries(androidManifest);
      }

      for (FsFile libraryBaseDir : libraryDirectories) {
        AndroidManifest libraryManifest = createLibraryAndroidManifest(libraryBaseDir);
        libraryManifest.setLibraryManifests(
            createLibraryManifests(libraryManifest, null));
        libraryManifests.add(libraryManifest);
      }
    }
    return libraryManifests;
  }

  private static AndroidManifest createLibraryAndroidManifest(FsFile libraryBaseDir) {
    return new AndroidManifest(libraryBaseDir.join(DEFAULT_MANIFEST_NAME), libraryBaseDir.join(Config.DEFAULT_RES_FOLDER), libraryBaseDir.join(Config.DEFAULT_ASSET_FOLDER));
  }

  private static Properties getProperties(FsFile propertiesFile) {
    Properties properties = new Properties();

    // return an empty Properties object if the propertiesFile does not exist
    if (!propertiesFile.exists()) return properties;

    InputStream stream;
    try {
      stream = propertiesFile.getInputStream();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try {
      try {
        properties.load(stream);
      } finally {
        stream.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return properties;
  }

  /**
   * Find valid library AndroidManifest files referenced from an already loaded AndroidManifest's
   * "project.properties" file.
   * @param androidManifest
   */
  private static List<FsFile> findLibraries(AndroidManifest androidManifest) {
    List<FsFile> libraryBaseDirs = new ArrayList<>();

    if (androidManifest.getResDirectory() != null) {
      FsFile baseDir = androidManifest.getResDirectory().getParent();
      final Properties properties = getProperties(baseDir.join("project.properties"));
      Properties overrideProperties = getProperties(baseDir.join("test-project.properties"));
      properties.putAll(overrideProperties);

      int libRef = 1;
      String lib;
      while ((lib = properties.getProperty("android.library.reference." + libRef)) != null) {
        FsFile libraryBaseDir = baseDir.join(lib);
        if (libraryBaseDir.isDirectory()) {
          // Ignore directories without any files
          FsFile[] libraryBaseDirFiles = libraryBaseDir.listFiles();
          if (libraryBaseDirFiles != null && libraryBaseDirFiles.length > 0) {
            libraryBaseDirs.add(libraryBaseDir);
          }
        }

        libRef++;
      }
    }
    return libraryBaseDirs;
  }

  protected String getManifestFileName() {
    return config.manifest().equals(Config.DEFAULT_MANIFEST)
        ? DEFAULT_MANIFEST_NAME : config.manifest().replaceAll("^(\\./)+", "");
  }

  protected AndroidManifest getOrCreateAndroidManifest(ManifestIdentifier identifier) {
    synchronized (appManifestsByFile) {
      AndroidManifest appManifest = appManifestsByFile.get(identifier);
      if (appManifest == null) {
        appManifest = createAppManifest(identifier);
        appManifestsByFile.put(identifier, appManifest);
      }
      // The AppManifest may STILL be null if no file was found.
      if (appManifest != null) {
        appManifest.setLibraryManifests(createLibraryManifests(appManifest, identifier.libraryDirs));
      }
      return appManifest;
    }
  }

  protected AndroidManifest createDummyManifest() {
    return new AndroidManifest(null, null, null, !config.packageName().isEmpty() ? config.packageName() : "org.robolectric.default") {
      @Override
      public int getTargetSdkVersion() {
        return SdkConfig.FALLBACK_SDK_VERSION;
      }
    };
  }

  private AndroidManifest createAppManifest(ManifestIdentifier identifier) {
    if (!identifier.manifestFile.exists()) {
      System.out.print("WARNING: No manifest file found at " + identifier.manifestFile.getPath() + ".");
      System.out.println("Falling back to the Android OS resources only.");
      System.out.println("To remove this warning, annotate your test class with @Config(manifest=Config.NONE).");
      return createDummyManifest();
    }

    Logger.debug("Robolectric assets directory: " + identifier.assetDir.getPath());
    Logger.debug("   Robolectric res directory: " + identifier.resDir.getPath());
    Logger.debug("   Robolectric manifest path: " + identifier.manifestFile.getPath());
    Logger.debug("    Robolectric package name: " + identifier.packageName);
    return new AndroidManifest(identifier.manifestFile, identifier.resDir, identifier.assetDir, identifier.packageName);
  }

  protected static class ManifestIdentifier {
    private final FsFile manifestFile;
    private final FsFile resDir;
    private final FsFile assetDir;
    private final String packageName;
    private final List<FsFile> libraryDirs;

    ManifestIdentifier(FsFile manifestFile, FsFile resDir, FsFile assetDir, String packageName,
                       List<FsFile> libraryDirs) {
      this.manifestFile = manifestFile;
      this.resDir = resDir;
      this.assetDir = assetDir;
      this.packageName = packageName;
      this.libraryDirs = libraryDirs != null ? libraryDirs : Collections.<FsFile>emptyList();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ManifestIdentifier that = (ManifestIdentifier) o;

      return assetDir.equals(that.assetDir)
          && libraryDirs.equals(that.libraryDirs)
          && manifestFile.equals(that.manifestFile)
          && resDir.equals(that.resDir)
          && ((packageName == null && that.packageName == null) || (packageName != null && packageName.equals(that.packageName)));
    }

    @Override
    public int hashCode() {
      int result = manifestFile.hashCode();
      result = 31 * result + resDir.hashCode();
      result = 31 * result + assetDir.hashCode();
      result = 31 * result + (packageName == null ? 0 : packageName.hashCode());
      result = 31 * result + libraryDirs.hashCode();
      return result;
    }
  }
}
