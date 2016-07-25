package org.robolectric;

import org.robolectric.annotation.Config;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.Fs;
import org.robolectric.res.FsFile;

import java.util.ArrayList;
import java.util.List;

/* package */ class MavenManifestFactory extends ManifestFactory {

  MavenManifestFactory(Config config) {
    super(config);
  }

  @Override
  public AndroidManifest create() {
    if (config.manifest().equals(Config.NONE)) {
      return createDummyManifest();
    }

    FsFile manifestFile = getBaseDir().join(getManifestFileName());
    FsFile baseDir = manifestFile.getParent();
    FsFile resDir = baseDir.join(config.resourceDir());
    FsFile assetDir = baseDir.join(config.assetDir());

    List<FsFile> libraryDirs = null;
    if (config.libraries().length > 0) {
      libraryDirs = new ArrayList<>();
      for (String libraryDirName : config.libraries()) {
        libraryDirs.add(baseDir.join(libraryDirName));
      }
    }

    ManifestIdentifier identifier = new ManifestIdentifier(manifestFile, resDir, assetDir, config.packageName(), libraryDirs);
    return getOrCreateAndroidManifest(identifier);
  }

  private static FsFile getBaseDir() {
    return Fs.currentDirectory();
  }
}
