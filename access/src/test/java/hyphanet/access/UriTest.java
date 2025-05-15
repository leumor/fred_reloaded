package hyphanet.access;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.MalformedURLException;
import org.junit.jupiter.api.Test;

class UriTest {
  @Test
  void brokenUskLinkResultsInMalformedUrlException() {
    assertThrows(MalformedURLException.class, () -> new Uri("USK@/broken/0"));
  }
}
