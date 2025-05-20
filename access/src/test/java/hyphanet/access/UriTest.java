package hyphanet.access;

import static org.junit.jupiter.api.Assertions.*;

import hyphanet.access.key.Usk;
import hyphanet.crypt.JcaProvider;
import java.net.MalformedURLException;
import java.security.Security;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UriTest {
  // Some URI for wAnnA? index
  private static final String WANNA_USK_1 =
      "USK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search/17/index_d51.xml";
  private static final String WANNA_SSK_1 =
      "SSK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search-17/index_d51.xml";
  private static final String WANNA_CHK_1 =
      "CHK@DTCDUmnkKFlrJi9UlDDVqXlktsIXvAJ~ZTseyx5cAZs,PmA2rLgWZKVyMXxSn-ZihSskPYDTY19uhrMwqDV-~Sk,AAICAAI/index_d51.xml";
  private static final String KSK_EXAMPLE = "KSK@gpl.txt";

  @BeforeAll
  static void setup() {
    Security.addProvider(new JcaProvider());
  }

  @Test
  void testSskForUSK() throws MalformedURLException {
    var uri1 = new Uri(WANNA_USK_1);
    var uri2 = new Uri(WANNA_SSK_1);

    assertEquals(uri2, ((Usk) uri1.createAccessKey()).toSsk().toUri());
    //    assertEquals(uri1, uri2.uskForSSK());
    //
    //    try {
    //      uri1.uskForSSK();
    //      fail("no exception throw!");
    //    } catch (IllegalStateException e) {
    //      // pass
    //    }
    //    try {
    //      uri2.sskForUSK();
    //      fail("no exception throw!");
    //    } catch (IllegalStateException e) {
    //      // pass
    //    }
    //
    //    try {
    //      new FreenetURI(WANNA_CHK_1).sskForUSK();
    //      fail("no exception throw!");
    //    } catch (IllegalStateException e) {
    //      // pass
    //    }
    //    try {
    //      new FreenetURI(WANNA_CHK_1).uskForSSK();
    //      fail("no exception throw!");
    //    } catch (IllegalStateException e) {
    //      // pass
    //    }
    //    try {
    //      new FreenetURI(
    //
    // "SSK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search-17XXXX/index_d51.xml")
    //          .sskForUSK();
    //      fail("no exception throw!");
    //    } catch (IllegalStateException e) {
    //      // pass
    //    }
    //    try {
    //      new FreenetURI(
    //
    // "SSK@5hH~39FtjA7A9~VXWtBKI~prUDTuJZURudDG0xFn3KA,GDgRGt5f6xqbmo-WraQtU54x4H~871Sho9Hz6hC-0RA,AQACAAE/Search17/index_d51.xml")
    //          .sskForUSK();
    //      fail("no exception throw!");
    //    } catch (IllegalStateException e) {
    //      // pass
    //    }
  }

  @Test
  void brokenSskLinkResultsInMalformedUrlException() throws MalformedURLException {
    var uri = new Uri("SSK@/broken-0");
    assertThrows(MalformedURLException.class, uri::createAccessKey);
  }

  @Test
  void addedValidSchemaPrefixesAreIgnored() throws MalformedURLException {
    for (String prefix :
        List.of(
            "freenet",
            "web+freenet",
            "ext+freenet",
            "hypha",
            "hyphanet",
            "web+hypha",
            "web+hyphanet",
            "ext+hypha",
            "ext+hyphanet")) {
      var uri = new Uri(prefix + ":" + WANNA_USK_1);
      assertEquals(WANNA_USK_1, uri.toString());
      uri = new Uri(prefix + ":" + WANNA_SSK_1);
      assertEquals(WANNA_SSK_1, uri.toString());
      uri = new Uri(prefix + ":" + WANNA_CHK_1);
      assertEquals(WANNA_CHK_1, uri.toString());
      uri = new Uri(prefix + ":" + KSK_EXAMPLE);
      assertEquals(KSK_EXAMPLE, uri.toString());
    }
  }

  @Test
  void brokenUskLinkResultsInMalformedUrlException() throws MalformedURLException {
    var uri = new Uri("USK@/broken/0");
    assertThrows(MalformedURLException.class, uri::createAccessKey);
  }

  @Test
  void sskCanBeCreatedWithoutRoutingKey() throws MalformedURLException {
    assertDoesNotThrow(() -> new Uri("SSK@"));
  }
}
