package org.robolectric.res;

import java.util.ArrayList;
import java.util.List;

public enum ResType {
  DRAWABLE,
  ATTR_DATA,
  BOOLEAN,
  COLOR,
  COLOR_STATE_LIST,
  DIMEN,
  FILE,
  FLOAT,
  FRACTION,
  INTEGER,
  LAYOUT,
  STYLE {
    @Override public TypedResource getValueWithType(XpathResourceXmlLoader.XmlNode xmlNode) {
      throw new UnsupportedOperationException();
    }
  },

  CHAR_SEQUENCE {
    @Override
    public TypedResource getValueWithType(XpathResourceXmlLoader.XmlNode xmlNode) {
      return new TypedResource<>(StringResources.proccessStringResources(xmlNode.getTextContent()), this);
    }
  },

  CHAR_SEQUENCE_ARRAY {
    @Override public TypedResource getValueWithType(XpathResourceXmlLoader.XmlNode xmlNode) {
      return extractScalarItems(xmlNode, CHAR_SEQUENCE_ARRAY, CHAR_SEQUENCE);
    }
  },

  INTEGER_ARRAY {
    @Override public TypedResource getValueWithType(XpathResourceXmlLoader.XmlNode xmlNode) {
      return extractScalarItems(xmlNode, INTEGER_ARRAY, INTEGER);
    }
  };

  private static TypedResource extractScalarItems(XpathResourceXmlLoader.XmlNode xmlNode, ResType arrayResType, ResType itemResType) {
    List<TypedResource> items = new ArrayList<>();
    for (XpathResourceXmlLoader.XmlNode item : xmlNode.selectElements("item")) {
      items.add(new TypedResource<>(item.getTextContent(), itemResType));
    }
    TypedResource[] typedResources = items.toArray(new TypedResource[items.size()]);
    return new TypedResource<>(typedResources, arrayResType);
  }

  public TypedResource getValueWithType(XpathResourceXmlLoader.XmlNode xmlNode) {
    return new TypedResource<>(xmlNode.getTextContent(), this);
  }

  /**
   * Parses a resource value to infer the type
   */
  public static ResType inferFromValue(String value) {
    if (value.startsWith("#")) {
      return ResType.COLOR;
    } else if ("true".equals(value) || "false".equals(value)) {
      return ResType.BOOLEAN;
    } else if (value.endsWith("dp") || value.endsWith("sp") || value.endsWith("pt") || value.endsWith("px") || value.endsWith("mm") || value.endsWith("in")) {
      return ResType.DIMEN;
    } else {
      try {
        Integer.parseInt(value);
        return ResType.INTEGER;
      } catch (NumberFormatException nfe) {}

      try {
        Float.parseFloat(value);
        return ResType.FRACTION;
      } catch (NumberFormatException nfe) {}


      return ResType.CHAR_SEQUENCE;
    }
  }
}
