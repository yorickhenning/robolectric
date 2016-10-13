package org.robolectric.util;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import org.robolectric.RuntimeEnvironment;

public class ContentProviderController<T extends ContentProvider> {
  private T contentProvider;

  private ContentProviderController(T contentProvider) {
    this.contentProvider = contentProvider;
  }

  public static <T extends ContentProvider> ContentProviderController<T> of(T contentProvider) {
    return new ContentProviderController<>(contentProvider);
  }

  public ContentProviderController<T> create() {
    return this;
  }

  public T get() {
    return contentProvider;
  }

  public ContentProviderController<T> shutdown() {
    contentProvider.shutdown();
    return this;
  }
}
