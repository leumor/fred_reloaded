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
package hyphanet.support.test;

/**
 * Utility class used throught test cases classes
 *
 * @author Alberto Bacchelli &lt;sback@freenetproject.org&gt;
 */
public final class UTFUtil {

    /**
     * Contains all Unicode characters except the low and high surrogates (they are no valid characters
     * and constructing strings with them will cause the JRE to replace them with the default replacement
     * character). Even 0x0000 is included.
     */
    public static final char[] ALL_CHARACTERS;
    //printable ascii symbols
    public static final char[] PRINTABLE_ASCII =
        {' ', '!', '@', '#', '$', '%', '^', '&', '(', ')', '+', '=', '{', '}', '[', ']', ':', ';', '\\',
            '\"', '\'', ',', '<', '>', '.', '?', '~', '`'};
    //stressed UTF chars values
    public static final char[] STRESSED_UTF = {
        //ÉâûĔĭņşÊãüĕĮŇŠËäýĖįňšÌåþėİŉŢÍæÿĘıŊţÎçĀęĲŋŤÏèāĚĳŌťÐéĂěĴōŦÑêăĜĵŎŧ
        'É', 'â', 'û', 'Ĕ', 'ĭ', 'ņ', 'ş', 'Ê', 'ã', 'ü', 'ĕ', 'Į', 'Ň', 'Š', 'Ë', 'ä', 'ý', 'Ė', 'į',
        'ň', 'š', 'Ì', 'å', 'þ', 'ė', 'İ', 'ŉ', 'Ţ', 'Í', 'æ', 'ÿ', 'Ę', 'ı', 'Ŋ', 'ţ', 'Î', 'ç', 'Ā',
        'ę', 'Ĳ', 'ŋ', 'Ť', 'Ï', 'è', 'ā', 'Ě', 'ĳ', 'Ō', 'ť', 'Ð', 'é', 'Ă', 'ě', 'Ĵ', 'ō', 'Ŧ', 'Ñ',
        'ê', 'ă', 'Ĝ', 'ĵ', 'Ŏ', 'ŧ',
        //ÒëĄĝĶŏŨÓìąĞķŐũÔíĆğĸőŪÕîćĠĹŒūÖïĈġĺœŬ×ðĉĢĻŔŭØñĊģļŕŮÙòċĤĽŖůÚóČĥľŗŰ
        'Ò', 'ë', 'Ą', 'ĝ', 'Ķ', 'ŏ', 'Ũ', 'Ó', 'ì', 'ą', 'Ğ', 'ķ', 'Ő', 'ũ', 'Ô', 'í', 'Ć', 'ğ', 'ĸ',
        'ő', 'Ū', 'Õ', 'î', 'ć', 'Ġ', 'Ĺ', 'Œ', 'ū', 'Ö', 'ï', 'Ĉ', 'ġ', 'ĺ', 'œ', 'Ŭ', '×', 'ð', 'ĉ',
        'Ģ', 'Ļ', 'Ŕ', 'ŭ', 'Ø', 'ñ', 'Ċ', 'ģ', 'ļ', 'ŕ', 'Ů', 'Ù', 'ò', 'ċ', 'Ĥ', 'Ľ', 'Ŗ', 'ů', 'Ú',
        'ó', 'Č', 'ĥ', 'ľ', 'ŗ', 'Ű',
        //ÛôčĦĿŘűÜõĎħŀřŲÝöďĨŁŚųÞ÷ĐĩłśŴßøđĪŃŜŵàùĒīńŝŶáúēĬŅŞŷ
        'Û', 'ô', 'č', 'Ħ', 'Ŀ', 'Ř', 'ű', 'Ü', 'õ', 'Ď', 'ħ', 'ŀ', 'ř', 'Ų', 'Ý', 'ö', 'ď', 'Ĩ', 'Ł',
        'Ś', 'ų', 'Þ', '÷', 'Đ', 'ĩ', 'ł', 'ś', 'Ŵ', 'ß', 'ø', 'đ', 'Ī', 'Ń', 'Ŝ', 'ŵ', 'à', 'ù', 'Ē',
        'ī', 'ń', 'ŝ', 'Ŷ', 'á', 'ú', 'ē', 'Ĭ', 'Ņ', 'Ş', 'ŷ'};
    /*
     * HTML entities ISO-88591
     * see for reference http://www.w3.org/TR/html4/sgml/entities.html#iso-88591
     */
    public static final String[][] HTML_ENTITIES_UTF = {
        //ISO 8859-1 Symbol Entities
        {"¡", "&iexcl;"}, {"¢", "&cent;"}, {"£", "&pound;"}, {"¤", "&curren;"}, {"¥", "&yen;"},
        {"¦", "&brvbar;"}, {"§", "&sect;"}, {"¨", "&uml;"}, {"©", "&copy;"}, {"ª", "&ordf;"},
        {"«", "&laquo;"}, {"¬", "&not;"}, {"\u00ad", "&shy;"}, {"®", "&reg;"}, {"¯", "&macr;"},
        {"°", "&deg;"}, {"±", "&plusmn;"}, {"²", "&sup2;"}, {"³", "&sup3;"}, {"´", "&acute;"},
        {"µ", "&micro;"}, {"¶", "&para;"}, {"·", "&middot;"}, {"¸", "&cedil;"}, {"¹", "&sup1;"},
        {"º", "&ordm;"}, {"»", "&raquo;"}, {"¼", "&frac14;"}, {"½", "&frac12;"}, {"¾", "&frac34;"},
        {"¿", "&iquest;"},
        //ISO 8859-1 Character Entities
        {"À", "&Agrave;"}, {"Á", "&Aacute;"}, {"Â", "&Acirc;"}, {"Ã", "&Atilde;"}, {"Ä", "&Auml;"},
        {"Å", "&Aring;"}, {"Æ", "&AElig;"}, {"Ç", "&Ccedil;"}, {"È", "&Egrave;"}, {"É", "&Eacute;"},
        {"Ê", "&Ecirc;"}, {"Ë", "&Euml;"}, {"Ì", "&Igrave;"}, {"Í", "&Iacute;"}, {"Î", "&Icirc;"},
        {"Ï", "&Iuml;"}, {"Ð", "&ETH;"}, {"Ñ", "&Ntilde;"}, {"Ò", "&Ograve;"}, {"Ó", "&Oacute;"},
        {"Ô", "&Ocirc;"}, {"Õ", "&Otilde;"}, {"Ö", "&Ouml;"}, {"×", "&times;"}, {"Ø", "&Oslash;"},
        {"Ù", "&Ugrave;"}, {"Ú", "&Uacute;"}, {"Û", "&Ucirc;"}, {"Ü", "&Uuml;"}, {"Ý", "&Yacute;"},
        {"Þ", "&THORN;"}, {"ß", "&szlig;"}, {"à", "&agrave;"}, {"á", "&aacute;"}, {"â", "&acirc;"},
        {"ã", "&atilde;"}, {"ä", "&auml;"}, {"å", "&aring;"}, {"æ", "&aelig;"}, {"ç", "&ccedil;"},
        {"è", "&egrave;"}, {"é", "&eacute;"}, {"ê", "&ecirc;"}, {"ë", "&euml;"}, {"ì", "&igrave;"},
        {"í", "&iacute;"}, {"î", "&icirc;"}, {"ï", "&iuml;"}, {"ð", "&eth;"}, {"ñ", "&ntilde;"},
        {"ò", "&ograve;"}, {"ó", "&oacute;"}, {"ô", "&ocirc;"}, {"õ", "&otilde;"}, {"ö", "&ouml;"},
        {"÷", "&divide;"}, {"ø", "&oslash;"}, {"ù", "&ugrave;"}, {"ú", "&uacute;"}, {"û", "&ucirc;"},
        {"ü", "&uuml;"}, {"ý", "&yacute;"}, {"þ", "&thorn;"}, {"ÿ", "&yuml;"},
        //Greek
        {"Α", "&Alpha;"}, {"Β", "&Beta;"}, {"Γ", "&Gamma;"}, {"Δ", "&Delta;"}, {"Ε", "&Epsilon;"},
        {"Ζ", "&Zeta;"}, {"Η", "&Eta;"}, {"Θ", "&Theta;"}, {"Ι", "&Iota;"}, {"Κ", "&Kappa;"},
        {"Λ", "&Lambda;"}, {"Μ", "&Mu;"}, {"Ν", "&Nu;"}, {"Ξ", "&Xi;"}, {"Ο", "&Omicron;"},
        {"Π", "&Pi;"}, {"Ρ", "&Rho;"}, {"Σ", "&Sigma;"}, {"Τ", "&Tau;"}, {"Υ", "&Upsilon;"},
        {"Φ", "&Phi;"}, {"Χ", "&Chi;"}, {"Ψ", "&Psi;"}, {"Ω", "&Omega;"}, {"α", "&alpha;"},
        {"β", "&beta;"}, {"γ", "&gamma;"}, {"δ", "&delta;"}, {"ε", "&epsilon;"}, {"ζ", "&zeta;"},
        {"η", "&eta;"}, {"θ", "&theta;"}, {"ι", "&iota;"}, {"κ", "&kappa;"}, {"λ", "&lambda;"},
        {"μ", "&mu;"}, {"ν", "&nu;"}, {"ξ", "&xi;"}, {"ο", "&omicron;"}, {"π", "&pi;"}, {"ρ", "&rho;"},
        {"ς", "&sigmaf;"}, {"σ", "&sigma;"}, {"τ", "&tau;"}, {"υ", "&upsilon;"}, {"φ", "&phi;"},
        {"χ", "&chi;"}, {"ψ", "&psi;"}, {"ω", "&omega;"}, {"ϑ", "&thetasym;"}, {"ϒ", "&upsih;"},
        {"ϖ", "&piv;"},
        //General Punctuation
        {"•", "&bull;"}, {"…", "&hellip;"}, {"′", "&prime;"}, {"″", "&Prime;"}, {"‾", "&oline;"},
        {"⁄", "&frasl;"},
        //Letterlike Symbols
        {"℘", "&weierp;"}, {"ℑ", "&image;"}, {"ℜ", "&real;"}, {"™", "&trade;"}, {"ℵ", "&alefsym;"},
        //Arrows
        {"←", "&larr;"}, {"↑", "&uarr;"}, {"→", "&rarr;"}, {"↓", "&darr;"}, {"↔", "&harr;"},
        {"↵", "&crarr;"}, {"⇐", "&lArr;"}, {"⇑", "&uArr;"}, {"⇒", "&rArr;"}, {"⇓", "&dArr;"},
        {"⇔", "&hArr;"},
        //Mathematical Operators
        {"∀", "&forall;"}, {"∂", "&part;"}, {"∃", "&exist;"}, {"∅", "&empty;"}, {"∇", "&nabla;"},
        {"∈", "&isin;"}, {"∉", "&notin;"}, {"∋", "&ni;"}, {"∏", "&prod;"}, {"∑", "&sum;"},
        {"−", "&minus;"}, {"∗", "&lowast;"}, {"√", "&radic;"}, {"∝", "&prop;"}, {"∞", "&infin;"},
        {"∠", "&ang;"}, {"∧", "&and;"}, {"∨", "&or;"}, {"∩", "&cap;"}, {"∪", "&cup;"}, {"∫", "&int;"},
        {"∴", "&there4;"}, {"∼", "&sim;"}, {"≅", "&cong;"}, {"≈", "&asymp;"}, {"≠", "&ne;"},
        {"≡", "&equiv;"}, {"≤", "&le;"}, {"≥", "&ge;"}, {"⊂", "&sub;"}, {"⊃", "&sup;"}, {"⊄", "&nsub;"},
        {"⊆", "&sube;"}, {"⊇", "&supe;"}, {"⊕", "&oplus;"}, {"⊗", "&otimes;"}, {"⊥", "&perp;"},
        {"⋅", "&sdot;"},
        //Miscellaneous Technical
        {"⌈", "&lceil;"}, {"⌉", "&rceil;"}, {"⌊", "&lfloor;"}, {"⌋", "&rfloor;"}, {"〈", "&lang;"},
        {"〉", "&rang;"},
        //Geometric Shapes
        {"◊", "&loz;"}, {"♠", "&spades;"}, {"♣", "&clubs;"}, {"♥", "&hearts;"}, {"♦", "&diams;"},
        //Latin Extended-A
        {"Œ", "&OElig;"}, {"œ", "&oelig;"}, {"Š", "&Scaron;"}, {"š", "&scaron;"}, {"Ÿ", "&Yuml;"},
        //Spacing Modifier Letters
        {"ˆ", "&circ;"}, {"˜", "&tilde;"},
        //General Punctuation
        {"\u2002", "&ensp;"}, {"\u2003", "&emsp;"}, {"\u2009", "&thinsp;"}, {"\u200c", "&zwnj;"},
        {"\u200d", "&zwj;"}, {"\u200e", "&lrm;"}, {"\u200f", "&rlm;"}, {"–", "&ndash;"},
        {"—", "&mdash;"}, {"‘", "&lsquo;"}, {"’", "&rsquo;"}, {"‚", "&sbquo;"}, {"“", "&ldquo;"},
        {"”", "&rdquo;"}, {"„", "&bdquo;"}, {"†", "&dagger;"}, {"‡", "&Dagger;"}, {"‰", "&permil;"},
        {"‹", "&lsaquo;"}, {"›", "&rsaquo;"}, {"€", "&euro;"}};

    static {
        ALL_CHARACTERS = new char[Character.MAX_VALUE - Character.MIN_VALUE + 1];

        for (int i = 0; i <= (Character.MAX_VALUE - Character.MIN_VALUE); ++i) {
            int characterValue = (Character.MIN_VALUE + i);

            // The low and high surrogates are no valid Unicode characters.
            if (characterValue >= Character.MIN_LOW_SURROGATE &&
                characterValue <= Character.MAX_LOW_SURROGATE) {
                ALL_CHARACTERS[i] = ' ';
            } else if (characterValue >= Character.MIN_HIGH_SURROGATE &&
                       characterValue <= Character.MAX_HIGH_SURROGATE) {
                ALL_CHARACTERS[i] = ' ';
            } else {
                ALL_CHARACTERS[i] = (char) characterValue;
            }
        }
    }

}
