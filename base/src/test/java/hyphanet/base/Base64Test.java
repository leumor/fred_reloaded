/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package hyphanet.base;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link Base64} class.
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
class Base64Test {

  // data from http://www.alanwood.net/unicode/unicode_samples.html
  static final String TEST_SAMPLE =
      "!5Aa¥¼ÑñĄąĲĳƏƐƕƺɖɞɫɷʱʬ˕˨o̕o̚ơo͡o"
          + "ΎΔδϠЉЩщӃԀԆԈԎԱԲաբסֶאבױ؟بحٍ۳܀ܐܠܘ݉ހސޤހި߄ߐ߰ߋ߹ࠀࠎࠏࠪ࠽ठःअठी३তঃঅ৩৵ਠਂਅਉਠੱઠઃઅઠૌ૩ଆଐଠୗ୩பஂஅபூ௩అఃఓఅౌ౩ಲಃಅಲೋ೩ഠഃഅഠൃ൩ෆංඑඣෆූกญกั๓ກຜໄ໓༣ཁངཱུངྵကဂု၄၍აზჵ჻ᄀᅙᇧᇸሀቻ፧፬ᎠᎫᏎᏴᐁᑦᕵᙧᚁᚈᚕ᚜ᚠᚳᛦᛰᜀᜄᜌᜊᜒᜠᜫᜪᜭᜯᝁᝊᝐᝊᝒᝠᝦᝮᝪᝲកឣខា៤᠀᠔ᡎᢥᢰᣇᣠᣴᤁᤥ᥅᥉ᥐᥞᥨᥲ᧠᧪᧴᧾ᨀᨁᨖᨔᨗᨠᨣᩯ᪁᪭ᬧᬀᬊᬧᭀ᭪ᮗᮀᮋᮗᮦ᮵ᰁᰘᰓᰯ᱅᱕ᱝᱰ᱿o᳐o᳢ᳩᳱᴂᴥᴽᵫḀẀẶỳἀὂᾑῼ—“‰※⁴⁾₃₌₢₣₪€o⃐o⃕o⃚o⃠℀℃№™⅛Ⅳⅸↂ←↯↻⇈∀∰⊇⋩⌂⌆⌣⌽␂␊␢␣⑀⑃⑆⑊③⑷⒌ⓦ┍┝╤╳▀▃▏░□▨◎◮☂☺♀♪✃✈❄➓⟐⟟⟥⟫⟰⟶⟺⟿⠀⠲⢖⣿⤄⤽⥈⥻⦀⦝⧰⧻⨇⨋⫚⫸⬀⬄⬉⬍ⰀⰉⰍⱙⱠⱥⱶⱺⲀⲑⲶⳂⴀⴆⴝⴢⴲⴶⵟⵥⶀⶆⶐⷖоⷠоⷩоⷶоⷿ⸁⸎⸨⸭⺀⺘⻂⻱⼀⼽⽺⿔⿰⿳⿷⿻々〒〣〰あぐるゞアヅヨヾㄆㄓㄝㄩㄱㄸㅪㆍ㆐㆕㆚㆟ㆠㆧㆯㆷㇰㇵㇺㇿ㈔㈲㊧㋮㌃㍻㎡㏵㐅㒅㝬㿜䷂䷫䷴䷾一憨田龥ꀀꅴꊩꒌ꒐꒡꒰꓆ꓐꓫꓻ꓿ꔁꔂꕝꕢꙂꙉꙮꚖꚠꛠꛕ꛰꛷꜁꜉ꜜꜟꜢꜮꝿꟿꠀꠇꠠꠤ꠪꠰꠶꠸꠹ꡁꡧꡳ꡷ꢝꢁꢍꢳ꣕ठ꣠ठ꣮ꣳꣻ꤅ꤎꤍꤪ꤮ꤰꤸꤷꥐ꥟ꥠꥪꥴꥼꦮꦀꦣꦮꦺ꧙ꨅꨍꨂꨬ꩖ꩠꩮꩴဂꩻꪀꪙꪒꪷ꫟ꯀꯌꯁꯧ꯹가뮀윸힣ힰퟎퟡퟻ豈朗歷館ﬀﬁﬗﭏﭐﰡﲼﷻo︠o︡o︢o︣︴︵﹃﹌﹖﹠﹩﹫ﹰﺗﺺﻼ３Ｆｶﾺ￹￺￼�𐀀𐀢𐁀𐁝𐂀𐂚𐃃𐃺𐄀𐄎𐄱𐄸𐅃𐅉𐅓𐆉𐆐𐆔𐆘𐆚𐇐𐇛𐇯𐇹𐊀𐊉𐊕𐊚𐊡𐊨𐊾𐋋𐌀𐌊𐌜𐌢𐌰𐌸𐍂𐍊𐎀𐎇𐎖𐎟𐐂𐐉𐐯𐑉𐑐𐑝𐑫𐑿𐒀𐒎𐒝𐒨𐠀𐠓𐠦𐠿𐡀𐡋𐡓𐡟𐤀𐤈𐤔𐤕𐤠𐤩𐤰𐤿𐨀𐨨𐨍𐨲𐩅𐩠𐩯𐩽𐩿𐬀𐬟𐬩𐬿𐭀𐭉𐭚𐭟𐭠𐭬𐭹𐭿𐰀𐰕𐰯𐱈𐹠𐹮𐹵𐹻𑂞𑂀𑂚𑂞𑂴𑃁𒀀𒀞𒅑𒍦𒐁𒐌𒐥𒑳𓀀𓅸𓉀𓐮𝁆𝂋𝃩𝃰𝄁𝄫𝅘𝅥𝅮𝇇𝌀𝌃𝌑𝍊𝍠𝍨𝍬𝍱𝓐𝕬𝝃𝟽🀀🀍🀒🀝🀴🁓🁮🂈🄀🄖🄭🆐🈐🈖🈪🉈𠀧𠤩𡨺𡽫𪜀𪮘𪾀𫜴勺卉善爨";
  static final String TEST_SAMPLE_STANDARD_ENCODING =
      "ITVBYcKlwrzDkcOxxITEhcSyxLPGj8aQxpXGusmWyZ7Jq8m3yrHKrMuVy6hvzJVvzJpvzJtvzaFvzo7OlM60z6DQidCp0YnTg9SA1IbUiNSO1LHUstWh1aLXoda215DXkdex2J/YqNit2Y3bs9yA3JDcoNyY3YnegN6Q3qTegN6o34TfkN+w34vfueCggOCgjuCgj+CgquCgveCkoOCkg+CkheCkoOClgOClqeCmpOCmg+CmheCnqeCnteCooOCoguCoheCoieCooOCpseCqoOCqg+CqheCqoOCrjOCrqeCshuCskOCsoOCtl+CtqeCuquCuguCuheCuquCvguCvqeCwheCwg+Cwk+CwheCxjOCxqeCysuCyg+CyheCysuCzi+CzqeC0oOC0g+C0heC0oOC1g+C1qeC3huC2guC2keC2o+C3huC3luC4geC4jeC4geC4seC5k+C6geC6nOC7hOC7k+C8o+C9geC9hOC9teC9hOC+teGAgOGAguGAr+GBhOGBjeGDkOGDluGDteGDu+GEgOGFmeGHp+GHuOGIgOGJu+GNp+GNrOGOoOGOq+GPjuGPtOGQgeGRpuGVteGZp+GageGaiOGaleGanOGaoOGas+GbpuGbsOGcgOGchOGcjOGciuGckuGcoOGcq+GcquGcreGcr+GdgeGdiuGdkOGdiuGdkuGdoOGdpuGdruGdquGdsuGegOGeo+GegeGetuGfpOGggOGglOGhjuGipeGisOGjh+GjoOGjtOGkgeGkpeGlheGlieGlkOGlnuGlqOGlsuGnoOGnquGntOGnvuGogOGogeGoluGolOGol+GooOGoo+Gpr+GqgeGqreGsp+GsgOGsiuGsp+GtgOGtquGul+GugOGui+Gul+GupuGuteGwgeGwmOGwk+Gwr+GxheGxleGxneGxsOGxv2/hs5Bv4bOi4bOp4bOx4bSC4bSl4bS94bWr4biA4bqA4bq24buz4byA4b2C4b6R4b+84oCU4oCc4oCw4oC74oG04oG+4oKD4oKM4oKi4oKj4oKq4oKsb+KDkG/ig5Vv4oOab+KDoOKEgOKEg+KEluKEouKFm+KFo+KFuOKGguKGkOKGr+KGu+KHiOKIgOKIsOKKh+KLqeKMguKMhuKMo+KMveKQguKQiuKQouKQo+KRgOKRg+KRhuKRiuKRouKRt+KSjOKTpuKUjeKUneKVpOKVs+KWgOKWg+KWj+KWkeKWoeKWqOKXjuKXruKYguKYuuKZgOKZquKcg+KciOKdhOKek+KfkOKfn+KfpeKfq+KfsOKftuKfuuKfv+KggOKgsuKiluKjv+KkhOKkveKliOKlu+KmgOKmneKnsOKnu+Koh+Koi+KrmuKruOKsgOKshOKsieKsjeKwgOKwieKwjeKxmeKxoOKxpeKxtuKxuuKygOKykeKytuKzguK0gOK0huK0neK0ouK0suK0tuK1n+K1peK2gOK2huK2kOK3ltC+4reg0L7it6nQvuK3ttC+4re/4riB4riO4rio4rit4rqA4rqY4ruC4rux4ryA4ry94r264r+U4r+w4r+z4r+34r+744CF44CS44Cj44Cw44GC44GQ44KL44Ke44Ki44OF44Oo44O+44SG44ST44Sd44Sp44Sx44S444Wq44aN44aQ44aV44aa44af44ag44an44av44a344ew44e144e644e/44iU44iy44qn44uu44yD4427446h44+145CF45KF452s47+c5LeC5Ler5Le05Le+5LiA5oao55Sw6b6l6oCA6oW06oqp6pKM6pKQ6pKh6pKw6pOG6pOQ6pOr6pO76pO/6pSB6pSC6pWd6pWi6pmC6pmJ6pmu6pqW6pqg6pug6puV6puw6pu36pyB6pyJ6pyc6pyf6pyi6pyu6p2/6p+/6qCA6qCH6qCg6qCk6qCq6qCw6qC26qC46qC56qGB6qGn6qGz6qG36qKd6qKB6qKN6qKz6qOV4KSg6qOg4KSg6qOu6qOz6qO76qSF6qSO6qSN6qSq6qSu6qSw6qS46qS36qWQ6qWf6qWg6qWq6qW06qW86qau6qaA6qaj6qau6qa66qeZ6qiF6qiN6qiC6qis6qmW6qmg6qmu6qm04YCC6qm76qqA6qqZ6qqS6qq36quf6q+A6q+M6q+B6q+n6q+56rCA666A7Jy47Z6j7Z6w7Z+O7Z+h7Z+77oCA7pm474iw76O/76SA76Sp76aM76is76yA76yB76yX762P762Q77Ch77K877e7b++4oG/vuKFv77iib++4o++4tO+4te+5g++5jO+5lu+5oO+5qe+5q++5sO+6l++6uu+7vO+8k++8pu+9tu++uu+/ue+/uu+/vO+/vfCQgIDwkICi8JCBgPCQgZ3wkIKA8JCCmvCQg4PwkIO68JCEgPCQhI7wkISx8JCEuPCQhYPwkIWJ8JCFk/CQhonwkIaQ8JCGlPCQhpjwkIaa8JCHkPCQh5vwkIev8JCHufCQioDwkIqJ8JCKlfCQiprwkIqh8JCKqPCQir7wkIuL8JCMgPCQjIrwkIyc8JCMovCQjLDwkIy48JCNgvCQjYrwkI6A8JCOh/CQjpbwkI6f8JCQgvCQkInwkJCv8JCRifCQkZDwkJGd8JCRq/CQkb/wkJKA8JCSjvCQkp3wkJKo8JCggPCQoJPwkKCm8JCgv/CQoYDwkKGL8JChk/CQoZ/wkKSA8JCkiPCQpJTwkKSV8JCkoPCQpKnwkKSw8JCkv/CQqIDwkKio8JCojfCQqLLwkKmF8JCpoPCQqa/wkKm98JCpv/CQrIDwkKyf8JCsqfCQrL/wkK2A8JCtifCQrZrwkK2f8JCtoPCQrazwkK258JCtv/CQsIDwkLCV8JCwr/CQsYjwkLmg8JC5rvCQubXwkLm78JGCnvCRgoDwkYKa8JGCnvCRgrTwkYOB8JKAgPCSgJ7wkoWR8JKNpvCSkIHwkpCM8JKQpfCSkbPwk4CA8JOFuPCTiYDwk5Cu8J2BhvCdgovwnYOp8J2DsPCdhIHwnYSr8J2FoPCdh4fwnYyA8J2Mg/CdjJHwnY2K8J2NoPCdjajwnY2s8J2NsfCdk5DwnZWs8J2dg/Cdn73wn4CA8J+AjfCfgJLwn4Cd8J+AtPCfgZPwn4Gu8J+CiPCfhIDwn4SW8J+ErfCfhpDwn4iQ8J+IlvCfiKrwn4mI8KCAp/CgpKnwoai68KG9q/CqnIDwqq6Y8Kq+gPCrnLTwr6Co8K+grPCvoYbwr6Sg";
  static final String TO_ENCODE =
      "Man is distinguished, not only by his reason, but by this singular "
          + "passion from other animals, which is a lust of the mind, that by a perseverance "
          + "of delight in the continued and indefatigable generation of knowledge, exceeds "
          + "the short vehemence of any carnal pleasure.";
  static final String TO_DECODE =
      "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ"
          + "1dCBieSB0aGlzIHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIG"
          + "x1c3Qgb2YgdGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY"
          + "29udGludWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRz"
          + "IHRoZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4";

  /**
   * Test the encode(byte[]) method against a well-known example (see <a
   * href="http://en.wikipedia.org/wiki/Base_64">reference</a>) to verify if it encode works
   * correctly.
   */
  @Test
  void testEncode() {
    byte[] aByteArrayToEncode = TO_ENCODE.getBytes(StandardCharsets.UTF_8);
    assertEquals(TO_DECODE, Base64.encode(aByteArrayToEncode));
  }

  /**
   * Test the decode(String) method against a well-known example (see <a
   * href="http://en.wikipedia.org/wiki/Base_64">reference</a>) to verify if it decode an already
   * encoded string correctly.
   */
  @Test
  void testDecode() throws Exception {
    String decodedString = new String(Base64.decode(TO_DECODE));
    assertEquals(TO_ENCODE, decodedString);
  }

  /**
   * Test the encodeStandard(byte[]) method This is the same as encode() from
   * generator/js/src/freenet/client/tools/Base64.java
   */
  @Test
  void testEncodeStandard() {
    byte[] aByteArrayToEncode = TEST_SAMPLE.getBytes(StandardCharsets.UTF_8);
    assertEquals(TEST_SAMPLE_STANDARD_ENCODING, Base64.encodeStandard(aByteArrayToEncode));
  }

  /**
   * Test the decodeStandard(byte[]) method. This is the same as decode() from
   * generator/js/src/freenet/client/tools/Base64.java
   */
  @Test
  void testDecodeStandard() throws Exception {
    String decodedString =
        new String(Base64.decodeStandard(TEST_SAMPLE_STANDARD_ENCODING), StandardCharsets.UTF_8);
    assertEquals(TEST_SAMPLE, decodedString);
  }

  /**
   * Test encode(byte[] in) and decode(String inStr) methods, to verify if they work correctly
   * together. It compares the string before encoding and with the one after decoding.
   */
  @Test
  void testEncodeDecode() {
    byte[] bytesDecoded;
    byte[] bytesToEncode = new byte[5];

    // byte upper bound
    bytesToEncode[0] = 127;
    bytesToEncode[1] = 64;
    bytesToEncode[2] = 0;
    bytesToEncode[3] = -64;
    // byte lower bound
    bytesToEncode[4] = -128;

    String aBase64EncodedString = Base64.encode(bytesToEncode);

    try {
      bytesDecoded = Base64.decode(aBase64EncodedString);
      assertArrayEquals(bytesToEncode, bytesDecoded);
    } catch (IllegalBase64Exception aException) {
      fail("Not expected exception thrown : " + aException.getMessage());
    }
  }

  /**
   * Test the encode(String,boolean) method to verify if the padding character '=' is correctly
   * placed.
   */
  @Test
  void testEncodePadding() {
    byte[][] methodBytesArray = {
      // three byte Array -> no padding char expected
      {4, 4, 4},
      // two byte Array -> one padding char expected
      {4, 4},
      // one byte Array -> two padding-chars expected
      {4}
    };
    String encoded;

    for (int i = 0; i < methodBytesArray.length; i++) {
      encoded = Base64.encode(methodBytesArray[i], true);
      if (i == 0)
      // no occurrences expected
      {
        assertEquals(-1, encoded.indexOf('='));
      } else {
        assertEquals(encoded.indexOf('='), encoded.length() - i);
      }
    }
  }

  /**
   * Test if the decode(String) method correctly raises an exception when providing a string with
   * non-Base64 characters.
   */
  @Test
  void testIllegalBaseCharacter() {
    //		TODO: check many other possible cases!
    assertThrows(IllegalBase64Exception.class, () -> Base64.decode("abcd=fghilmn"));
  }

  /**
   * Test if the decode(String) method correctly raises an exception when providing a string with a
   * wrong Base64 length. (as we can consider not-padded strings too, the only wrong lengths are the
   * ones where -> number MOD 4 = 1).
   */
  @Test
  void testIllegalBaseLength() {
    // most interesting case
    assertThrows(IllegalBase64Exception.class, () -> Base64.decode("a"));
  }

  /** Random test */
  @Test
  void testRandom() throws IllegalBase64Exception {
    int iter;
    Random r = new Random(1234);
    for (iter = 0; iter < 1000; iter++) {
      byte[] b = new byte[r.nextInt(64)];
      for (int i = 0; i < b.length; i++) {
        b[i] = (byte) (r.nextInt(256));
      }
      String encoded = Base64.encode(b);
      byte[] decoded = Base64.decode(encoded);
      assertEquals(decoded.length, b.length, "length mismatch");

      for (int i = 0; i < b.length; i++) {
        assertEquals(
            b[i],
            decoded[i],
            "data mismatch: index "
                + i
                + " of "
                + b.length
                + " should be 0x"
                + Integer.toHexString(b[i] & 0xFF)
                + " was 0x"
                + Integer.toHexString(decoded[i] & 0xFF));
      }
    }
  }
}
