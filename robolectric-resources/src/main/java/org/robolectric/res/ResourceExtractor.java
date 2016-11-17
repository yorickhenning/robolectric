package org.robolectric.res;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

public class ResourceExtractor extends ResourceIndex {
  private static final Logger LOGGER = Logger.getLogger(ResourceExtractor.class.getName());

  private final String packageName;
  private Integer maxUsedInt = null;
  private Integer generatedIdStart = null;

  public ResourceExtractor(ResourcePath resourcePath) {
    packageName = resourcePath.getPackageName();

    if (resourcePath.getRClass() != null) {
      gatherResourceIdsAndNames(resourcePath.getRClass(), packageName);
    }

    if (resourcePath.getInternalRClass() != null) {
      gatherResourceIdsAndNames(resourcePath.getInternalRClass(), packageName);
    }
  }

  private synchronized void gatherResourceIdsAndNames(Class<?> rClass, String packageName) {
    for (Class innerClass : rClass.getClasses()) {
      for (Field field : innerClass.getDeclaredFields()) {
        if (field.getType().equals(Integer.TYPE) && Modifier.isStatic(field.getModifiers())) {
          String section = innerClass.getSimpleName();
          int id;
          try {
            id = field.getInt(null);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }

          if (!section.equals("styleable")) {
            String fieldName = field.getName();
            ResName resName = new ResName(packageName, section, fieldName);

            resourceNameToId.put(resName, id);
            resourceIdToResName.put(id, resName);
          }
        }
      }
    }
  }

  @Override
  public synchronized Integer getResourceId(ResName resName) {
    if (!packageName.equals(resName.packageName)) {
      return null;
    }
    Integer id = resourceNameToId.get(resName);
    if (id == null && ("android".equals(resName.packageName) || "".equals(resName.packageName))) {
      if (maxUsedInt == null) {
        maxUsedInt = resourceIdToResName.isEmpty() ? 0 : Collections.max(resourceIdToResName.keySet());
        generatedIdStart = maxUsedInt;
      }
      id = ++maxUsedInt;
      resourceNameToId.put(resName, id);
      resourceIdToResName.put(id, resName);
      LOGGER.fine("no id mapping found for " + resName.getFullyQualifiedName() + "; assigning ID #0x" + Integer.toHexString(id));
    }
    if (id == null) return 0;

    return id;
  }

  @Override
  public synchronized ResName getResName(int resourceId) {
    return resourceIdToResName.get(resourceId);
  }

  @Override public Collection<String> getPackages() {
    return Collections.singletonList(packageName);
  }

  @Override public String toString() {
    return "ResourceExtractor{" +
        "package=" + packageName +
        '}';
  }
}