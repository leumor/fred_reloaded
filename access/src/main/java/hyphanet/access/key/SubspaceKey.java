package hyphanet.access.key;

import java.util.regex.Pattern;

public interface SubspaceKey {
  Pattern DOC_NAME_WITH_EDITION_PATTERN = Pattern.compile(".*-(\\d+)");

  String getDocName();
}
