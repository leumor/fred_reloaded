package hyphanet.support.field;

import com.machinezoo.noexception.Exceptions;
import hyphanet.support.Base64;
import hyphanet.support.IllegalBase64Exception;
import hyphanet.support.io.LineReader;
import hyphanet.support.io.Readers;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.util.Collections.emptyMap;

/**
 * A hierarchical key-value data structure that supports nested field sets separated by dots.
 * This class provides a thread-safe implementation for storing and managing hierarchical data
 * in a simple text format.
 *
 * <p>The data structure supports two main types of entries:</p>
 * <ul>
 *   <li>Direct key-value pairs (e.g., {@code DirectKey=Value})</li>
 *   <li>Nested subsets (e.g., {@code Subset.Key=Value} or {@code Subset
 *   .Subset.Key=Value})</li>
 * </ul>
 *
 * <p>The file format supports:</p>
 * <ul>
 *   <li>Optional headers (prefixed with #)</li>
 *   <li>Key-value pairs separated by '='</li>
 *   <li>Nested structures using '.' as separator</li>
 *   <li>Optional Base64 encoding for values</li>
 *   <li>Optional end marker</li>
 * </ul>
 *
 * <p>Example format:</p>
 * <pre>
 * # Header1
 * # Header2
 * key1=value1
 * nested.key=value2
 * nested.deep.key=value3
 * End
 * </pre>
 *
 * <p>This implementation is thread-safe, with all public methods being
 * synchronized
 * on the SimpleFieldSet instance.</p>
 *
 * @author amphibian
 * @see hyphanet.support.Base64
 */
public class SimpleFieldSet {

    /**
     * The character used to separate hierarchy levels in field keys.
     * <p>
     * For example, in the key {@code parent.child.value}, the dots are MULTI_LEVEL_CHAR
     * separators that create a hierarchical structure with "parent" and "child" as nested
     * levels.
     * </p>
     */
    public static final char MULTI_LEVEL_CHAR = '.';

    /**
     * The character used to separate multiple values for the same key.
     * <p>
     * When a key has multiple values, they are separated by this character during
     * serialization and deserialization. Only used when allowMultiple is set to true during
     * parsing.
     * </p>
     * <p>Example: {@code key=value1;value2;value3}</p>
     */
    public static final char MULTI_VALUE_CHAR = ';';

    /**
     * The character used to separate keys from their values in the field set.
     * <p>
     * Used during parsing and serialization to distinguish between the key and value portions
     * of each entry.
     * </p>
     * <p>Example: {@code key=value}</p>
     */
    public static final char KEYVALUE_SEPARATOR_CHAR = '=';
    /**
     * Logger instance for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SimpleFieldSet.class);

    /**
     * An empty string array constant.
     */
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * Creates a new empty SimpleFieldSet with default settings. Base64 encoding will not be
     * forced for all values.
     *
     * @see #SimpleFieldSet(boolean)
     */
    public SimpleFieldSet() {
        this(false);
    }

    /**
     * Creates a new empty SimpleFieldSet with specified Base64 encoding behavior.
     *
     * @param alwaysUseBase64 When {@code true}, all values will be encoded in Base64 format
     *                        during serialization regardless of content. When {@code false},
     *                        Base64 encoding is only used when necessary (e.g., for binary
     *                        data or special characters).
     */
    public SimpleFieldSet(boolean alwaysUseBase64) {
        values = new HashMap<>();
        subsets = null;
        this.alwaysUseBase64 = alwaysUseBase64;
    }

    /**
     * Constructs a SimpleFieldSet by parsing the provided string content. Format:
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * @param content       The string containing the field set data in the above format.
     * @param allowMultiple If {@code true}, allows multiple values for a single key separated
     *                      by {@link #MULTI_VALUE_CHAR}. For example:
     *                      {@code key=value1;value2;value3}
     * @param allowBase64   If {@code true}, values encoded in Base64 format will be
     *                      automatically decoded during parsing.
     *
     * @throws IOException If the content string is malformed, too short, or cannot be properly
     *                     parsed according to the field set format
     * @see #MULTI_VALUE_CHAR
     * @see #KEYVALUE_SEPARATOR_CHAR
     * @see hyphanet.support.Base64
     */
    public SimpleFieldSet(String content, boolean allowMultiple, boolean allowBase64)
        throws IOException {
        this(false);

        StringReader sr = new StringReader(content);
        BufferedReader br = new BufferedReader(sr);
        read(Readers.fromBufferedReader(br), allowMultiple, allowBase64);
    }

    /**
     * Constructs a SimpleFieldSet by parsing content from a BufferedReader with specified
     * parsing options.
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * @param br            The BufferedReader containing the field set data in the above
     *                      format.
     * @param allowMultiple If {@code true}, allows multiple values for a single key separated
     *                      by {@link #MULTI_VALUE_CHAR}. For example:
     *                      {@code key=value1;value2;value3}
     * @param allowBase64   If {@code true}, values encoded in Base64 format will be
     *                      automatically decoded during parsing.
     * @param alwaysBase64  If {@code true}, all values will be encoded in Base64 format
     *                      regardless of content. Thus, it can store anything in values
     *                      including newlines, special chars such as = etc.
     *
     * @throws IOException If there is an error reading from the BufferedReader or if the
     *                     content is malformed
     * @see #MULTI_VALUE_CHAR
     * @see #KEYVALUE_SEPARATOR_CHAR
     * @see hyphanet.support.Base64
     */
    public SimpleFieldSet(
        BufferedReader br, boolean allowMultiple, boolean allowBase64,
        boolean alwaysBase64) throws IOException {
        this(alwaysBase64);
        read(Readers.fromBufferedReader(br), allowMultiple, allowBase64);
    }

    /**
     * Constructs a SimpleFieldSet by parsing content from a BufferedReader with multi-value
     * support.
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     * <p>
     * This constructor is a convenience method that calls
     * {@link #SimpleFieldSet(BufferedReader, boolean, boolean, boolean)} with default settings
     * for Base64 encoding.
     *
     * @param br            The BufferedReader containing the field set data in the above
     *                      format.
     * @param allowMultiple If {@code true}, allows multiple values for a single key separated
     *                      by {@link #MULTI_VALUE_CHAR}. For example:
     *                      {@code key=value1;value2;value3}
     *
     * @throws IOException If there is an error reading from the BufferedReader or if the
     *                     content is malformed
     * @see #SimpleFieldSet(BufferedReader, boolean, boolean, boolean)
     * @see #MULTI_VALUE_CHAR
     */
    public SimpleFieldSet(BufferedReader br, boolean allowMultiple) throws IOException {
        this(br, allowMultiple, false, false);
    }

    /**
     * Creates a deep copy of an existing SimpleFieldSet instance. This constructor performs a
     * complete copy of all data including:
     * <ul>
     *   <li>All key-value pairs</li>
     *   <li>All nested subsets</li>
     *   <li>Header information</li>
     *   <li>End marker</li>
     *   <li>Base64 encoding settings</li>
     * </ul>
     *
     * @param sfs The source SimpleFieldSet to copy from. Must not be {@code null}
     *
     * @see #SimpleFieldSet(Map, Map, String[], String, boolean)
     */
    public SimpleFieldSet(SimpleFieldSet sfs) {
        this(sfs.values, sfs.subsets, sfs.header, sfs.endMarker, sfs.alwaysUseBase64);
    }

    /**
     * Constructs a SimpleFieldSet by reading content from a LineReader with specified parsing
     * parameters.
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     * <p>
     * This constructor is a convenience method that calls
     * {@link #SimpleFieldSet(LineReader, int, int, boolean, boolean, boolean)} with Base64
     * decoding disabled.
     *
     * @param lis            The LineReader containing the field set data in the above format.
     * @param maxLineLength  The maximum allowed length for a single line in characters
     * @param lineBufferSize The size of the buffer used for reading lines. Should be at least
     *                       as large as maxLineLength
     * @param utf8OrIso88591 If {@code true}, content is read as UTF-8; if {@code false},
     *                       content is read as ISO-8859-1
     * @param allowMultiple  If {@code true}, allows multiple values for a single key separated
     *                       by {@link #MULTI_VALUE_CHAR}. For example:
     *                       {@code key=value1;value2;value3}
     *
     * @throws IOException If there is an error reading from the LineReader or if the content
     *                     is malformed
     * @see #MULTI_VALUE_CHAR
     * @see #SimpleFieldSet(LineReader, int, int, boolean, boolean, boolean)
     */
    public SimpleFieldSet(
        LineReader lis, int maxLineLength, int lineBufferSize,
        boolean utf8OrIso88591, boolean allowMultiple) throws IOException {
        this(lis, maxLineLength, lineBufferSize, utf8OrIso88591, allowMultiple, false);
    }


    /**
     * Constructs a SimpleFieldSet by reading content from a LineReader with full parsing
     * configuration. This constructor provides complete control over line reading and parsing
     * behavior.
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * @param lis            The LineReader containing the field set data in the above format.
     * @param maxLineLength  The maximum allowed length for a single line in characters. Lines
     *                       exceeding this length will cause an IOException
     * @param lineBufferSize The size of the buffer used for reading lines. Should be at least
     *                       as large as maxLineLength
     * @param utf8OrIso88591 If {@code true}, content is read as UTF-8; if {@code false},
     *                       content is read as ISO-8859-1
     * @param allowMultiple  If {@code true}, allows multiple values for a single key separated
     *                       by {@link #MULTI_VALUE_CHAR}. For example:
     *                       {@code key=value1;value2;value3}
     * @param allowBase64    If {@code true}, values encoded in Base64 format will be
     *                       automatically decoded during parsing.
     *
     * @throws IOException If there is an error reading from the LineReader, if the content is
     *                     malformed, or if a line exceeds maxLineLength
     * @see #MULTI_VALUE_CHAR
     * @see hyphanet.support.Base64
     */
    public SimpleFieldSet(
        LineReader lis, int maxLineLength, int lineBufferSize,
        boolean utf8OrIso88591, boolean allowMultiple, boolean allowBase64)
        throws IOException {
        this(false);
        read(lis, maxLineLength, lineBufferSize, utf8OrIso88591, allowMultiple, allowBase64);
    }

    /**
     * Internal constructor used for creating SimpleFieldSet instances with predefined data.
     * This constructor is primarily used by the copy constructor and other internal methods
     * that need to create a new instance with existing data structures.
     *
     * @param values          A map containing the direct key-value pairs. If {@code null}, an
     *                        empty map will be used
     * @param subsets         A map containing nested SimpleFieldSets keyed by their prefix.
     *                        Can be {@code null} if there are no nested sets
     * @param header          An array of strings representing the header comments. Each string
     *                        will be prefixed with "# " during serialization. Can be
     *                        {@code null}
     * @param endMarker       The string to use as the end marker when serializing. If
     *                        {@code null}, defaults to "End"
     * @param alwaysUseBase64 If {@code true}, all values will be encoded in Base64 format
     *                        regardless of content
     *
     * @see #SimpleFieldSet(SimpleFieldSet)
     */
    private SimpleFieldSet(
        Map<String, String> values, Map<String, SimpleFieldSet> subsets, String[] header,
        String endMarker, boolean alwaysUseBase64) {
        this.values = new HashMap<>(values);
        this.subsets = (subsets == null) ? null : new HashMap<>(subsets);
        this.header = header;
        this.endMarker = endMarker;
        this.alwaysUseBase64 = alwaysUseBase64;
    }

    /**
     * An iterator implementation that traverses through a hierarchical structure of key-value
     * pairs in a SimpleFieldSet. This iterator provides access to all keys in the structure,
     * including those in nested subsets.
     *
     * <p>For example, given the following structure:</p>
     * <pre>
     * key1=value1
     * key2.sub2=value2
     * key1.sub=value3
     * </pre>
     *
     * <p>The iterator will return: key1, key2.sub2, key1.sub</p>
     *
     * <p>This implementation is thread-safe, using synchronization on the
     * parent SimpleFieldSet instance.</p>
     */
    public class KeyIterator implements Iterator<String> {
        /**
         * Creates a new KeyIterator with the specified prefix.
         *
         * <p>The prefix is prepended to all keys returned by this iterator. For
         * example, if the prefix is "myPrefix" and the key is "key1", the iterator will return
         * "myprefixkey1".</p>
         *
         * @param prefix the prefix to prepend to all keys. Must not be null.
         */
        public KeyIterator(String prefix) {
            synchronized (SimpleFieldSet.this) {
                this.prefix = prefix;
                valuesIterator = values.keySet().iterator();
                subsetIterator = subsets != null ? subsets.keySet().iterator() : null;

                initializeSubIterator();
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p>Returns true if there are more keys to iterate over in this
         * SimpleFieldSet or any of its nested subsets.</p>
         *
         * @return {@code true} if there are more keys to iterate over, {@code false} otherwise
         */
        @Override
        public boolean hasNext() {
            synchronized (SimpleFieldSet.this) {
                while (true) {
                    if (valuesIterator.hasNext()) {
                        return true;
                    }
                    if ((subIterator != null) && subIterator.hasNext()) {
                        return true;
                    }

                    subIterator = null;

                    if (subsetIterator != null && subsetIterator.hasNext()) {
                        String key = subsetIterator.next();
                        assert subsets != null;
                        SimpleFieldSet fs = subsets.get(key);
                        String newPrefix = prefix + key + MULTI_LEVEL_CHAR;
                        subIterator = fs.keyIterator(newPrefix);
                    } else {
                        return false;
                    }
                }
            }
        }

        /**
         * {@inheritDoc}
         *
         * @return the next key in the iteration
         *
         * @see #nextKey()
         */
        @Override
        public final String next() {
            return nextKey();
        }

        /**
         * Returns the next key in the iteration.
         *
         * <p>This method returns the next key in the iteration, including the
         * configured prefix. It traverses through direct values first, then proceeds with
         * nested subsets.</p>
         *
         * @return the next key in the iteration, with the prefix prepended
         *
         * @throws NoSuchElementException if there are no more keys to iterate over
         */
        public String nextKey() {
            synchronized (SimpleFieldSet.this) {
                if (valuesIterator.hasNext()) {
                    return prefix + valuesIterator.next();
                }

                return getNextFromSubsets();
            }
        }

        /**
         * Retrieves the next key from nested subsets. This method handles the traversal of the
         * hierarchical structure, keeping track of the current position and managing
         * transitions between different levels of nesting.
         *
         * @return The next key in the subset hierarchy
         *
         * @throws NoSuchElementException if there are no more elements to iterate over
         */
        private String getNextFromSubsets() {
            String result = null;

            while (true) {
                if (subIterator != null && subIterator.hasNext()) {
                    if (result != null) {
                        // If we have a result, and we have a next value, return
                        return result;
                    }
                    result = subIterator.next();
                    if (subIterator.hasNext()) {
                        // If we have a result, and we have a next value, return
                        return result;
                    }
                }

                if (!moveToNextSubset()) {
                    if (result == null) {
                        throw new NoSuchElementException();
                    }
                    return result;
                }
            }
        }

        /**
         * Advances the iteration to the next subset in the hierarchy.
         * <p>
         * This method:
         * <ul>
         *   <li>Clears the current sub-iterator</li>
         *   <li>Checks if there are more subsets to process</li>
         *   <li>Creates a new sub-iterator for the next subset if available</li>
         * </ul>
         * </p>
         *
         * @return {@code true} if successfully moved to the next subset, {@code false} if
         * there are no more subsets
         */
        private boolean moveToNextSubset() {
            subIterator = null;
            if (subsetIterator == null || !subsetIterator.hasNext()) {
                return false;
            }

            String key = subsetIterator.next();
            assert subsets != null;
            SimpleFieldSet fs = subsets.get(key);
            String newPrefix = prefix + key + MULTI_LEVEL_CHAR;
            subIterator = fs.keyIterator(newPrefix);
            return true;
        }

        /**
         * Initializes the subset iterator for nested traversal.
         *
         * <p>This method sets up the initial state for traversing nested
         * subsets when there are no more direct values to iterate over. It handles the
         * complexity of finding the first valid subset to iterate.</p>
         */
        private void initializeSubIterator() {
            // Continue searching if: no direct values left AND subsets exist
            // AND more subsets to
            // check
            boolean shouldContinueSearch =
                !valuesIterator.hasNext() && subsetIterator != null &&
                subsetIterator.hasNext();

            while (shouldContinueSearch) {
                String name = subsetIterator.next();
                assert subsets != null;
                SimpleFieldSet fs = name != null ? subsets.get(name) : null;

                if (fs != null) {
                    // Create the new prefix by appending the subset name and
                    // separator
                    String newPrefix = prefix + name + MULTI_LEVEL_CHAR;
                    subIterator = fs.keyIterator(newPrefix);

                    // If this subset has elements, we've found what we're
                    // looking for
                    if (subIterator.hasNext()) {
                        break;
                    }

                    // Reset if the subset is empty
                    subIterator = null;
                }

                shouldContinueSearch = !valuesIterator.hasNext() && subsetIterator.hasNext();
            }
        }

        /**
         * Iterator for the direct key-value pairs in the current SimpleFieldSet
         */
        private final Iterator<String> valuesIterator;

        /**
         * Iterator for the subset names in the current SimpleFieldSet
         */
        private final Iterator<String> subsetIterator;

        /**
         * Prefix to be prepended to all keys returned by this iterator
         */
        private final String prefix;

        /**
         * Iterator for the current subset being processed
         */
        private KeyIterator subIterator;
    }

    /**
     * Splits a string into an array of substrings using the {@link #MULTI_LEVEL_CHAR} as
     * delimiter. accepting empty strings at both ends.
     * <p>
     * Java 7 version of String.split() trims the extra delimiters at each end.
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>{@code split("a;b;c")} returns {@code ["a", "b", "c"]}</li>
     *   <li>{@code split(";a;b;c;;")} returns {@code ["", "a", "b", "c", "", ""]}</li>
     *   <li>{@code split("simple")} returns {@code ["simple"]}</li>
     *   <li>{@code split("")} returns an empty array</li>
     *   <li>{@code split(null)} returns an empty array</li>
     * </ul>
     *
     * @param string the string to split, may be null or empty
     *
     * @return an array of substrings, never null but may be empty
     *
     * @see #MULTI_LEVEL_CHAR
     */
    public static String[] split(String string) {
        if (string == null) {
            return EMPTY_STRING_ARRAY;
        }
        // Java 7 version of String.split() trims the extra delimiters at each end.
        int emptyAtStart = 0;
        while (emptyAtStart < string.length() &&
               string.charAt(emptyAtStart) == MULTI_VALUE_CHAR) {
            emptyAtStart++;
        }
        if (emptyAtStart == string.length()) {
            String[] ret = new String[string.length()];
            Arrays.fill(ret, "");
            return ret;
        }
        int emptyAtEnd = 0;
        for (int i = string.length() - 1;
             i >= 0 && string.charAt(i) == MULTI_VALUE_CHAR; i--) {
            emptyAtEnd++;
        }
        string = string.substring(emptyAtStart, string.length() - emptyAtEnd);
        String[] split = string.split(String.valueOf(MULTI_VALUE_CHAR)); // slower???
        if (emptyAtStart != 0 || emptyAtEnd != 0) {
            String[] ret = new String[emptyAtStart + split.length + emptyAtEnd];
            System.arraycopy(split, 0, ret, emptyAtStart, split.length);
            split = ret;
            for (int i = 0; i < split.length; i++) {
                if (split[i] == null) {
                    split[i] = "";
                }
            }
        }
        return split;
    }

    /**
     * Creates a SimpleFieldSet by reading and parsing content from an input stream with Base64
     * encoding and decoding disabled.
     *
     * <p>This is a convenience method that calls
     * {@link #readFrom(InputStream, boolean, boolean, boolean)} with Base64 encoding and
     * decoding disabled. The input stream is expected to contain data in the following
     * format:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>The method automatically handles character encoding by wrapping the input stream
     * in a BufferedReader using UTF-8 charset.</p>
     *
     * @param is            The input stream containing the field set data
     * @param allowMultiple If {@code true}, allows multiple values for a single key separated
     *                      by {@link #MULTI_VALUE_CHAR}. For example:
     *                      {@code key=value1;value2;value3}
     *
     * @return A new SimpleFieldSet instance containing the parsed data
     *
     * @throws IOException If there is an error reading from the input stream or if the content
     *                     is malformed
     * @see #readFrom(InputStream, boolean, boolean, boolean)
     * @see #MULTI_VALUE_CHAR
     */
    public static SimpleFieldSet readFrom(InputStream is, boolean allowMultiple)
        throws IOException {
        return readFrom(is, allowMultiple, false, false);
    }

    /**
     * Creates a SimpleFieldSet by reading and parsing content from an input stream.
     *
     * <p>The input stream is expected to contain data in the following format:</p>
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>The method automatically handles character encoding by wrapping the input stream
     * in a BufferedReader using UTF-8 charset.</p>
     *
     * @param is            The input stream containing the field set data
     * @param allowMultiple If {@code true}, allows multiple values for a single key separated
     *                      by {@link #MULTI_VALUE_CHAR}. For example:
     *                      {@code key=value1;value2;value3}
     * @param allowBase64   If {@code true}, values encoded in Base64 format will be
     *                      automatically decoded during parsing
     * @param alwaysBase64  If {@code true}, all values will be encoded in Base64 format
     *                      regardless of content. Thus, it can store anything in values
     *                      including newlines, special chars such as = etc.
     *
     * @return A new SimpleFieldSet instance containing the parsed data
     *
     * @throws IOException If there is an error reading from the input stream or if the content
     *                     is malformed
     * @see #MULTI_VALUE_CHAR
     * @see #KEYVALUE_SEPARATOR_CHAR
     * @see hyphanet.support.Base64
     */
    public static SimpleFieldSet readFrom(
        InputStream is, boolean allowMultiple,
        boolean allowBase64, boolean alwaysBase64)
        throws IOException {

        try (var bis = new BufferedInputStream(is);
             var isr = new InputStreamReader(bis, StandardCharsets.UTF_8);
             var br = new BufferedReader(isr)) {

            return new SimpleFieldSet(br, allowMultiple, allowBase64, alwaysBase64);
        }
    }

    /**
     * Creates a SimpleFieldSet by reading and parsing content from a file with Base64 encoding
     * and decoding disabled.
     *
     * <p>This is a convenience method that creates an input stream from the file and
     * delegates to {@link #readFrom(InputStream, boolean, boolean, boolean)} with Base64
     * encoding and decoding disabled. The file is expected to contain data in the following
     * format:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>The method automatically handles character encoding by using UTF-8 and ensures
     * proper resource cleanup.</p>
     *
     * @param f             The file containing the field set data
     * @param allowMultiple If {@code true}, allows multiple values for a single key separated
     *                      by {@link #MULTI_VALUE_CHAR}. For example:
     *                      {@code key=value1;value2;value3}
     *
     * @return A new SimpleFieldSet instance containing the parsed data
     *
     * @throws IOException If there is an error reading from the file or if the content is
     *                     malformed
     * @see #readFrom(InputStream, boolean)
     * @see #MULTI_VALUE_CHAR
     */
    public static SimpleFieldSet readFrom(File f, boolean allowMultiple) throws IOException {
        try (var fis = new FileInputStream(f)) {
            return readFrom(fis, allowMultiple);
        }
    }

    /**
     * Retrieves the value associated with the specified key from this field set.
     *
     * <p>This method handles both direct key-value pairs and nested hierarchical keys
     * separated by {@link #MULTI_LEVEL_CHAR}. For nested keys, it traverses the hierarchy to
     * find the corresponding value.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>For direct key: {@code get("key1")} returns the value for "key1"</li>
     *   <li>For nested key: {@code get("parent.child")} returns the value in the nested
     *   structure</li>
     * </ul>
     *
     * <p>The method is thread-safe through synchronization.</p>
     *
     * @param key the key whose associated value is to be returned. Can be a direct key or a
     *            dot-separated path for nested values. May be null
     *
     * @return the value associated with the specified key, or null if:
     * <ul>
     *   <li>the key is null</li>
     *   <li>the key doesn't exist</li>
     *   <li>the key refers to a subset rather than a value</li>
     * </ul>
     *
     * @see #MULTI_LEVEL_CHAR
     */
    @SuppressWarnings("NullTernary")
    public synchronized @Nullable String get(@Nullable String key) {
        if (key == null) {
            return null;
        }

        return switch (Integer.valueOf(key.indexOf(MULTI_LEVEL_CHAR))) {
            case -1 -> values.get(key);
            case 0 -> {
                SimpleFieldSet subFieldSet = subset("");
                yield subFieldSet != null ? subFieldSet.get(key.substring(1)) : null;
            }
            case Integer pos -> {
                if (subsets == null) {
                    yield null;
                }
                SimpleFieldSet fs = subsets.get(key.substring(0, pos));
                yield fs != null ? fs.get(key.substring(pos + 1)) : null;
            }
        };
    }

    /**
     * Retrieves all values associated with the specified key, handling both single values and
     * multiple values separated by {@link #MULTI_VALUE_CHAR}.
     *
     * <p>This method supports both direct key-value pairs and nested hierarchical keys
     * separated by {@link #MULTI_LEVEL_CHAR}. For nested keys, it traverses the hierarchy to
     * find the corresponding values.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Single value: {@code key=value} returns {@code ["value"]}</li>
     *   <li>Multiple values: {@code key=value1;value2} returns {@code ["value1", "value2"]}
     *   </li>
     *   <li>Nested key: {@code parent.child=value} accessed via {@code getAll("parent
     *   .child")}</li>
     * </ul>
     *
     * @param key the key whose associated values are to be returned. Can be a direct key or a
     *            dot-separated path for nested values. May be null
     *
     * @return an array containing all values associated with the key, or an empty array if:
     * <ul>
     *   <li>the key is null</li>
     *   <li>the key doesn't exist</li>
     *   <li>the key refers to a subset rather than a value</li>
     * </ul>
     *
     * @see #MULTI_VALUE_CHAR
     * @see #MULTI_LEVEL_CHAR
     */
    public String[] getAll(@Nullable String key) {
        if (key == null) {
            return EMPTY_STRING_ARRAY;
        }

        String value = get(key);
        return value == null ? EMPTY_STRING_ARRAY : split(value);
    }

    /**
     * Retrieves all values associated with the specified key and encodes them in Base64
     * format.
     *
     * <p>This method functions similarly to {@link #getAll(String)} but ensures all returned
     * values are Base64 encoded. It supports both direct key-value pairs and nested
     * hierarchical keys separated by {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Single value: {@code key=hello} returns {@code ["aGVsbG8="]} (Base64 encoded)</li>
     *   <li>Multiple values: {@code key=a;b} returns {@code ["YQ==", "Yg=="]} (each value
     *   Base64 encoded)</li>
     *   <li>Nested key: {@code parent.child=test} accessed via {@code getAllEncoded("parent
     *   .child")}</li>
     * </ul>
     *
     * @param key the key whose associated values are to be returned in Base64 encoded format.
     *            Can be a direct key or a dot-separated path for nested values. May be null
     *
     * @return an array containing all Base64 encoded values associated with the key, or an
     * empty array if:
     * <ul>
     *   <li>the key is null</li>
     *   <li>the key doesn't exist</li>
     *   <li>the key refers to a subset rather than a value</li>
     * </ul>
     *
     * @see #getAll(String)
     * @see #MULTI_VALUE_CHAR
     * @see #MULTI_LEVEL_CHAR
     * @see hyphanet.support.Base64
     */
    @SuppressWarnings("RedundantThrows")
    public String[] getAllEncoded(@Nullable String key) throws IllegalBase64Exception {
        if (key == null) {
            return EMPTY_STRING_ARRAY;
        }

        String value = get(key);
        if (value == null) {
            return EMPTY_STRING_ARRAY;
        }

        return Arrays.stream(split(value)).map(Exceptions.sneak().function(Base64::decodeUTF8))
                     .toArray(String[]::new);
    }

    /**
     * Copies all entries from the specified SimpleFieldSet into this instance, overwriting any
     * existing values with matching keys.
     *
     * <p>This method performs a deep copy of the source field set, including all nested
     * subsets and their values. The operation is atomic and thread-safe.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct values are overwritten: if this has {@code key1=old} and source has
     *       {@code key1=new}, the result will be {@code key1=new}</li>
     *   <li>Nested values are merged recursively: {@code parent.child=value} will be
     *       copied maintaining the hierarchy</li>
     * </ul>
     *
     * @param fs the source SimpleFieldSet whose entries are to be copied into this instance.
     *           If null, this method does nothing
     *
     * @see #MULTI_LEVEL_CHAR
     */
    public synchronized void putAllOverwrite(@Nullable SimpleFieldSet fs) {
        if (fs == null) {
            return;
        }

        values.putAll(fs.values);

        if (fs.subsets != null) {
            fs.subsets.forEach((key, sourceFS) -> {
                if (subsets == null) {
                    subsets = new HashMap<>();
                }

                subsets.compute(key, (k, targetFS) -> {
                    if (targetFS != null) {
                        targetFS.putAllOverwrite(sourceFS);
                        return targetFS;
                    }
                    return sourceFS;
                });
            });
        }
    }

    /**
     * Associates a single value with the specified key in this field set.
     *
     * <p>This method supports both direct key-value pairs and nested hierarchical keys
     * separated by {@link #MULTI_LEVEL_CHAR}. For nested keys, it creates the necessary
     * hierarchy of subsets automatically.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code putSingle("key1", "value1")} creates {@code key1=value1}</li>
     *   <li>Nested key: {@code putSingle("parent.child", "value")} creates a nested
     *   structure</li>
     *   <li>Null value: {@code putSingle("key", null)} removes the entry if it exists</li>
     * </ul>
     *
     * <p>If the key contains multiple levels, intermediate subsets will be created as needed.
     * Any existing value for the same key will be overwritten.</p>
     *
     * @param key   the key with which the specified value is to be associated. Can be a direct
     *              key or a dot-separated path for nested values
     * @param value the value to be associated with the specified key. If null, removes the
     *              entry if it exists
     *
     * @throws IllegalStateException if the key already exists
     * @see #MULTI_LEVEL_CHAR
     */
    public void putSingle(@Nullable String key, @Nullable String value) {
        if (key == null || value == null) {
            return;
        }

        if (!put(key, value, false, false, false)) {
            throw new IllegalStateException(
                "Value already exists: " + value + " but want to set " + key + " to " + value);
        }
    }

    /**
     * Appends a value to an existing key or creates a new key-value pair if the key doesn't
     * exist.
     *
     * <p>This method supports both direct key-value pairs and nested hierarchical keys
     * separated by {@link #MULTI_LEVEL_CHAR}. If the key already exists, the new value is
     * appended using the {@link #MULTI_VALUE_CHAR} separator.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>New key: {@code putAppend("key1", "value1")} creates {@code key1=value1}</li>
     *   <li>Existing key: If {@code key1=value1} exists, {@code putAppend("key1", "value2")}
     *       results in {@code key1=value1;value2}</li>
     *   <li>Nested key: {@code putAppend("parent.child", "value")} appends to the nested
     *   structure</li>
     * </ul>
     *
     * @param key   the key with which the specified value is to be associated. Cannot be null
     * @param value the value to be appended to the specified key. Cannot be null
     *
     * @see #MULTI_VALUE_CHAR
     * @see #MULTI_LEVEL_CHAR
     */
    public void putAppend(String key, String value) {
        if (value == null) {
            return;
        }

        put(key, value, true, false, false);
    }

    /**
     * Associates a value with the specified key, overwriting any existing value.
     *
     * <p>This method supports both direct key-value pairs and nested hierarchical keys
     * separated by {@link #MULTI_LEVEL_CHAR}. For nested keys, it creates the necessary
     * hierarchy of subsets automatically.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code putOverwrite("key1", "value1")} creates or overwrites with
     *   {@code key1=value1}</li>
     *   <li>Nested key: {@code putOverwrite("parent.child", "value")} creates or overwrites
     *   in the nested structure</li>
     *   <li>Existing value: If {@code key1=old} exists, {@code putOverwrite("key1", "new")}
     *   results in {@code key1=new}</li>
     * </ul>
     *
     * <p>Unlike {@link #putAppend}, this method always replaces the existing value instead
     * of appending to it.</p>
     *
     * @param key   the key with which the specified value is to be associated. Cannot be null
     * @param value the value to be associated with the specified key. Cannot be null
     *
     * @see #MULTI_LEVEL_CHAR
     * @see #putAppend(String, String)
     */
    public void putOverwrite(String key, String value) {
        if (value == null) {
            return;
        }

        put(key, value, false, true, false);
    }

    /**
     * Associates the string representation of an integer value with the specified key.
     *
     * <p>This method converts the integer value to a string and stores it in the field set.
     * It supports both direct key-value pairs and nested hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code put("key1", 42)} creates {@code key1=42}</li>
     *   <li>Nested key: {@code put("parent.child", 123)} creates a nested structure</li>
     * </ul>
     *
     * <p>The integer value is converted to its decimal string representation using
     * {@link Integer#toString(int)}.</p>
     *
     * @param key   the key with which the string representation of the value is to be
     *              associated. Cannot be null
     * @param value the integer value to be associated with the specified key
     *
     * @see #MULTI_LEVEL_CHAR
     * @see #putSingle(String, String)
     */
    public void put(String key, int value) {
        // Use putSingle so it does the intern check
        putSingle(key, Integer.toString(value));
    }

    /**
     * Associates the string representation of a long value with the specified key.
     *
     * <p>This method converts the long value to a string and stores it in the field set.
     * It supports both direct key-value pairs and nested hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code put("key1", 9223372036854775807L)} creates {@code key1
     *   =9223372036854775807}</li>
     *   <li>Nested key: {@code put("parent.child", -42L)} creates a nested structure</li>
     * </ul>
     *
     * <p>The long value is converted to its decimal string representation using
     * {@link Long#toString(long)}.</p>
     *
     * @param key   the key with which the string representation of the value is to be
     *              associated. Cannot be null
     * @param value the long value to be associated with the specified key
     *
     * @see #MULTI_LEVEL_CHAR
     * @see #putSingle(String, String)
     */
    public void put(String key, long value) {
        putSingle(key, Long.toString(value));
    }

    /**
     * Associates the string representation of a short value with the specified key.
     *
     * <p>This method converts the short value to a string and stores it in the field set.
     * It supports both direct key-value pairs and nested hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code put("key1", (short)42)} creates {@code key1=42}</li>
     *   <li>Nested key: {@code put("parent.child", (short)-128)} creates a nested
     *   structure</li>
     * </ul>
     *
     * <p>The short value is converted to its decimal string representation using
     * {@link Short#toString(short)}.</p>
     *
     * @param key   the key with which the string representation of the value is to be
     *              associated. Cannot be null
     * @param value the short value to be associated with the specified key
     *
     * @see #MULTI_LEVEL_CHAR
     * @see #putSingle(String, String)
     */
    public void put(String key, short value) {
        putSingle(key, Short.toString(value));
    }

    /**
     * Associates the string representation of a character value with the specified key.
     *
     * <p>This method converts the character value to a string and stores it in the field set.
     * It supports both direct key-value pairs and nested hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code put("key1", 'A')} creates {@code key1=A}</li>
     *   <li>Nested key: {@code put("parent.child", '$')} creates a nested structure</li>
     * </ul>
     *
     * <p>The character value is converted to its string representation using
     * {@link Character#toString(char)}.</p>
     *
     * @param key the key with which the string representation of the value is to be
     *            associated. Cannot be null
     * @param c   the character value to be associated with the specified key
     *
     * @see #MULTI_LEVEL_CHAR
     * @see #putSingle(String, String)
     */
    public void put(String key, char c) {
        putSingle(key, Character.toString(c));
    }

    /**
     * Associates the string representation of a boolean value with the specified key.
     *
     * <p>This method converts the boolean value to a string and stores it in the field set.
     * It supports both direct key-value pairs and nested hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code put("key1", true)} creates {@code key1=true}</li>
     *   <li>Nested key: {@code put("parent.child", false)} creates a nested structure</li>
     * </ul>
     *
     * <p>The boolean value is converted to its string representation using
     * {@link Boolean#toString(boolean)}.</p>
     *
     * @param key the key with which the string representation of the value is to be
     *            associated. Cannot be null
     * @param b   the boolean value to be associated with the specified key
     *
     * @see #MULTI_LEVEL_CHAR
     * @see #putSingle(String, String)
     */
    public void put(String key, boolean b) {
        putSingle(key, Boolean.toString(b));
    }

    /**
     * Associates the string representation of a double value with the specified key.
     *
     * <p>This method converts the double value to a string and stores it in the field set.
     * It supports both direct key-value pairs and nested hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code put("key1", 3.14)} creates {@code key1=3.14}</li>
     *   <li>Nested key: {@code put("parent.child", -0.5)} creates a nested structure</li>
     * </ul>
     *
     * <p>The double value is converted to its string representation using
     * {@link Double#toString(double)}.</p>
     *
     * @param key the key with which the string representation of the value is to be
     *            associated. Cannot be null
     * @param d   the double value to be associated with the specified key
     *
     * @see #MULTI_LEVEL_CHAR
     * @see #putSingle(String, String)
     */
    public void put(String key, double d) {
        putSingle(key, Double.toString(d));
    }

    /**
     * Associates the Base64 encoded string representation of a byte array with the specified
     * key.
     *
     * <p>This method converts the byte array to a Base64 encoded string and stores it in the
     * field set. It supports both direct key-value pairs and nested hierarchical keys
     * separated by {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code put("key1", new byte[]{1,2,3})} creates {@code key1=AQID}
     *   (Base64 encoded)</li>
     *   <li>Nested key: {@code put("parent.child", new byte[]{65,66,67})} creates a nested
     *   structure</li>
     * </ul>
     *
     * <p>The byte array is always encoded using Base64 encoding to ensure safe storage and
     * transmission of binary data.</p>
     *
     * @param key   the key with which the Base64 encoded string representation of the value is
     *              to be associated. Cannot be null
     * @param bytes the byte array to be encoded and associated with the specified key. If
     *              null, removes the entry if it exists
     *
     * @see #MULTI_LEVEL_CHAR
     * @see hyphanet.support.Base64
     */
    public void put(String key, byte[] bytes) {
        putSingle(key, Base64.encode(bytes));
    }

    /**
     * Writes the complete field set to a Writer in unordered format.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets. The output format follows the standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>Values that require Base64 encoding (based on the {@code alwaysUseBase64} setting)
     * can be automatically encoded during writing.</p>
     *
     * @param w the Writer to which the field set will be written. Cannot be null. Keep in mind
     *          that a Writer is not necessarily UTF-8!
     *
     * @throws IOException           if an error occurs while writing to the Writer
     * @throws IllegalStateException if a subset that was previously added becomes null during
     *                               serialization
     * @see #writeToOrdered(Writer, String, boolean, boolean)
     */
    public void writeTo(Writer w) throws IOException {
        writeTo(w, "", false, false);
    }

    /**
     * Writes the complete field set to a Writer in alphabetically ordered format.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets. All keys are written in alphabetical order. The output format
     * follows the standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>Values that require Base64 encoding (based on the {@code alwaysUseBase64} setting)
     * can be automatically encoded during writing. The method ensures consistent output by
     * maintaining alphabetical order of keys at each level.</p>
     *
     * @param w the Writer to which the field set will be written. Cannot be null. Keep in mind
     *          that a Writer is not necessarily UTF-8!
     *
     * @throws IOException           if an error occurs while writing to the Writer
     * @throws IllegalStateException if a subset that was previously added becomes null during
     *                               serialization
     * @see #writeTo(Writer)
     */
    public void writeToOrdered(Writer w) throws IOException {
        writeToOrdered(w, "", false, false);
    }

    /**
     * Converts this SimpleFieldSet to its string representation.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets into a string format. The output follows the standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>Values that require Base64 encoding (based on the {@code alwaysUseBase64} setting)
     * can be automatically encoded. The output is equivalent to calling
     * {@link #writeTo(Writer)} with a StringWriter.</p>
     *
     * @return a string representation of this SimpleFieldSet
     */
    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        try {
            writeTo(sw);
        } catch (IOException e) {
            logger.error("WTF?!: {} in toString()!", e, e);
        }
        return sw.toString();
    }

    /**
     * Converts this SimpleFieldSet to its string representation with keys in alphabetical
     * order.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets into a string format, with all keys sorted alphabetically at each
     * level. The output follows the standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>Values that require Base64 encoding (based on the {@code alwaysUseBase64} setting)
     * will be automatically encoded. The output is equivalent to calling
     * {@link #writeToOrdered(Writer)} with a StringWriter.</p>
     *
     * @return a string representation of this SimpleFieldSet with alphabetically ordered keys
     */
    public String toOrderedString() {
        StringWriter sw = new StringWriter();
        try {
            writeToOrdered(sw);
        } catch (IOException e) {
            logger.error("WTF?!: {} in toOrderedString()!", e, e);
        }
        return sw.toString();
    }

    /**
     * Converts this SimpleFieldSet to its string representation with keys in alphabetical
     * order and all values Base64 encoded.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets into a string format, with all keys sorted alphabetically at each
     * level. All values are encoded in Base64 format regardless of content. The output follows
     * the standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=BASE64VALUE1
     * key2=BASE64VALUE2
     * nested.key=BASE64VALUE3
     * End
     * </pre>
     *
     * <p>The output is equivalent to calling {@link #writeToOrdered(Writer)} with a
     * StringWriter and Base64 encoding enabled for all values.</p>
     *
     * @return a string representation of this SimpleFieldSet with alphabetically ordered keys
     * and Base64 encoded values
     *
     * @see hyphanet.support.Base64
     */
    public String toOrderedStringWithBase64() {
        StringWriter sw = new StringWriter();
        try {
            writeToOrdered(sw, "", false, true);
        } catch (IOException e) {
            logger.error("WTF?!: {} in toOrderedStringWithBase64()!", e, e);
        }
        return sw.toString();
    }

    /**
     * Returns the end marker string used for serialization of this field set.
     *
     * <p>The end marker is a string that marks the end of the field set data during
     * serialization. If no custom end marker was set, this method returns the default value
     * "End".</p>
     *
     * <p><b>Example output format:</b></p>
     * <pre>
     * key1=value1
     * key2=value2
     * EndMarkerString
     * </pre>
     *
     * @return the current end marker string, or "End" if no custom end marker was set
     *
     * @see #setEndMarker(String)
     */
    public String getEndMarker() {
        return endMarker;
    }

    /**
     * Sets a custom end marker string for this field set's serialization.
     *
     * <p>The end marker is a string that marks the end of the field set data during
     * serialization. If not set, the default value "End" is used.</p>
     *
     * <p><b>Example output format with custom end marker:</b></p>
     * <pre>
     * key1=value1
     * key2=value2
     * CustomEndMarker
     * </pre>
     *
     * @param s the new end marker string to use. If null, the default "End" will be used
     *
     * @see #getEndMarker()
     */
    public void setEndMarker(@Nullable String s) {
        endMarker = s;
    }

    /**
     * Retrieves a nested SimpleFieldSet associated with the specified key.
     *
     * <p>This method returns the subset of fields under the specified key in the hierarchy.
     * The key can be a direct subset name or a dot-separated path to a deeper nested
     * subset.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct subset: {@code subset("config")} returns the SimpleFieldSet under
     *   "config"</li>
     *   <li>Nested path: {@code subset("parent.child")} returns the SimpleFieldSet under
     *   the nested path</li>
     * </ul>
     *
     * <p>The method is thread-safe through synchronization.</p>
     *
     * @param key the key identifying the subset to retrieve. Can be a direct key or a
     *            dot-separated path. May be null
     *
     * @return the SimpleFieldSet associated with the specified key, or null if:
     * <ul>
     *   <li>the key is null</li>
     *   <li>the key doesn't exist</li>
     *   <li>the key refers to a value rather than a subset</li>
     * </ul>
     *
     * @see #getSubset(String)
     */
    public synchronized SimpleFieldSet subset(String key) {
        if (subsets == null) {
            return null;
        }
        int idx = key.indexOf(MULTI_LEVEL_CHAR);
        if (idx == -1) {
            return subsets.get(key);
        }

        String before = key.substring(0, idx);
        SimpleFieldSet fs = subsets.get(before);
        if (fs == null) {
            return null;
        }

        return fs.subset(key.substring(idx + 1));
    }

    /**
     * Retrieves a nested SimpleFieldSet associated with the specified key, throwing an
     * exception if the key exists but refers to a value instead of a subset.
     *
     * <p>This method returns the subset of fields under the specified key in the hierarchy.
     * The key can be a direct subset name or a dot-separated path to a deeper nested
     * subset.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct subset: {@code getSubset("config")} returns the SimpleFieldSet under
     *   "config"</li>
     *   <li>Nested path: {@code getSubset("parent.child")} returns the SimpleFieldSet under
     *   the nested path</li>
     * </ul>
     *
     * <p>The method is thread-safe through synchronization.</p>
     *
     * @param key the key identifying the subset to retrieve. Can be a direct key or a
     *            dot-separated path
     *
     * @return the SimpleFieldSet associated with the specified key
     *
     * @throws FSParseException if the subset doesn't exist
     * @see #subset(String)
     */
    public synchronized SimpleFieldSet getSubset(String key) throws FSParseException {
        SimpleFieldSet fs = subset(key);
        if (fs == null) {
            throw new FSParseException("No such subset " + key);
        }
        return fs;
    }

    /**
     * Returns an iterator over all keys in this SimpleFieldSet, including nested keys.
     *
     * <p>The iterator traverses through all keys in this SimpleFieldSet, including both
     * direct values and nested subsets. For nested keys, the full path is returned using
     * {@link #MULTI_LEVEL_CHAR} as separator.</p>
     *
     * <p><b>Example iteration over this structure:</b></p>
     * <pre>
     * key1=value1
     * nested.key=value2
     * nested.deep.key=value3
     * </pre>
     *
     * <p>The iterator will return these keys in sequence:</p>
     * <ul>
     *   <li>{@code key1}</li>
     *   <li>{@code nested.key}</li>
     *   <li>{@code nested.deep.key}</li>
     * </ul>
     *
     * <p>The method is thread-safe through synchronization on the SimpleFieldSet instance.</p>
     *
     * @return an iterator over all keys in this SimpleFieldSet
     *
     * @see KeyIterator
     * @see #keyIterator(String)
     */
    public Iterator<String> keyIterator() {
        return new KeyIterator("");
    }

    /**
     * Returns an iterator over all keys in this SimpleFieldSet with a specified prefix.
     *
     * <p>The iterator traverses through all keys in this SimpleFieldSet, including both
     * direct values and nested subsets, prepending the specified prefix to each key. For
     * nested keys, the full path is returned using {@link #MULTI_LEVEL_CHAR} as
     * separator.</p>
     *
     * <p><b>Example with prefix "myPrefix.":</b></p>
     * <pre>
     * Original structure:
     * key1=value1
     * nested.key=value2
     *
     * Iterator returns:
     * myPrefix.key1
     * myPrefix.nested.key
     * </pre>
     *
     * <p>The method is thread-safe through synchronization on the SimpleFieldSet
     * instance.</p>
     *
     * @param prefix the prefix to prepend to all returned keys. Must not be null
     *
     * @return an iterator over all keys in this SimpleFieldSet with the specified prefix
     *
     * @see KeyIterator
     * @see #keyIterator()
     */
    public KeyIterator keyIterator(String prefix) {
        return new KeyIterator(prefix);
    }

    /**
     * Returns an iterator over only the top-level keys in this SimpleFieldSet.
     *
     * <p>Unlike {@link #keyIterator()}, this method only returns keys at the root level,
     * not including nested keys. The iterator provides a flat view of the immediate key-value
     * pairs and subset names in this SimpleFieldSet.</p>
     *
     * <p><b>Example for this structure:</b></p>
     * <pre>
     * key1=value1
     * key2=value2
     * nested.key=value3
     * deep.nested.key=value4
     * </pre>
     *
     * <p>The iterator will return only:</p>
     * <ul>
     *   <li>{@code key1}</li>
     *   <li>{@code key2}</li>
     *   <li>{@code nested}</li>
     *   <li>{@code deep}</li>
     * </ul>
     *
     * <p>The method is thread-safe through synchronization on the SimpleFieldSet instance.</p>
     *
     * @return an iterator over the top-level keys in this SimpleFieldSet
     *
     * @see #keyIterator()
     * @see KeyIterator
     * @see #keyIterator(String)
     */
    public Iterator<String> toplevelKeyIterator() {
        return values.keySet().iterator();
    }

    /**
     * Returns an unmodifiable map of all direct key-value pairs in this SimpleFieldSet.
     *
     * <p>This method returns only the immediate key-value pairs at the current level,
     * excluding any nested subsets. The returned map is unmodifiable to preserve
     * encapsulation.</p>
     *
     * <p><b>Example for this structure:</b></p>
     * <pre>
     * key1=value1
     * key2=value2
     * nested.key=value3
     * deep.nested.key=value4
     * </pre>
     *
     * <p>The returned map will contain only:</p>
     * <ul>
     *   <li>{@code key1 -> value1}</li>
     *   <li>{@code key2 -> value2}</li>
     * </ul>
     *
     * @return an unmodifiable map containing all direct key-value pairs in this
     * SimpleFieldSet. Returns an empty map if there are no direct values
     */
    public Map<String, String> directKeyValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Returns an unmodifiable set of all direct keys in this SimpleFieldSet.
     *
     * <p>This method returns only the immediate keys at the current level,
     * excluding any nested subset keys. The returned set is unmodifiable to preserve
     * encapsulation.</p>
     *
     * <p><b>Example for this structure:</b></p>
     * <pre>
     * key1=value1
     * key2=value2
     * nested.key=value3
     * deep.nested.key=value4
     * </pre>
     *
     * <p>The returned set will contain only:</p>
     * <ul>
     *   <li>{@code key1}</li>
     *   <li>{@code key2}</li>
     * </ul>
     *
     * @return an unmodifiable set containing all direct keys in this SimpleFieldSet. Returns
     * an empty set if there are no direct keys
     */
    public Set<String> directKeys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /**
     * Returns an unmodifiable map of all direct nested subsets in this SimpleFieldSet.
     *
     * <p>This method returns only the immediate nested SimpleFieldSets at the current level,
     * excluding any deeper nested structures. The returned map is unmodifiable to preserve
     * encapsulation.</p>
     *
     * <p><b>Example for this structure:</b></p>
     * <pre>
     * key1=value1
     * config.setting1=value2
     * config.setting2=value3
     * deep.nested.key=value4
     * </pre>
     *
     * <p>The returned map will contain only:</p>
     * <ul>
     *   <li>{@code config -> SimpleFieldSet} (containing setting1 and setting2)</li>
     *   <li>{@code deep -> SimpleFieldSet} (containing the nested structure)</li>
     * </ul>
     *
     * @return an unmodifiable map containing all direct nested SimpleFieldSets. Returns an
     * empty map if there are no nested subsets
     */
    public Map<String, SimpleFieldSet> directSubsets() {
        return subsets == null ? emptyMap() : Collections.unmodifiableMap(subsets);
    }

    /**
     * Associates a SimpleFieldSet with the specified key, but only if both the key and
     * SimpleFieldSet are non-null and the SimpleFieldSet is not empty.
     *
     * <p>This is a tolerant version of the put operation that silently ignores invalid
     * or empty inputs instead of throwing exceptions. It's useful when you want to
     * conditionally add nested structures only if they contain data.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Valid case: {@code tput("config", nonEmptyFieldSet)} adds the nested
     *   structure</li>
     *   <li>Ignored case: {@code tput("config", emptyFieldSet)} does nothing</li>
     *   <li>Ignored case: {@code tput(null, fieldSet)} does nothing</li>
     * </ul>
     *
     * @param key the key with which to associate the nested SimpleFieldSet. If null, the
     *            method returns without doing anything
     * @param fs  the SimpleFieldSet to be associated with the key. If null or empty, the
     *            method returns without doing anything
     *
     * @see #put(String, SimpleFieldSet)
     */
    public void tput(@Nullable String key, @Nullable SimpleFieldSet fs) {
        if (key == null || fs == null || fs.isEmpty()) {
            return;
        }
        put(key, fs);
    }

    /**
     * Associates a nested SimpleFieldSet with the specified key.
     *
     * <p>This method adds or replaces a nested SimpleFieldSet at the specified key location.
     * It supports both direct subset names and hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct subset: {@code put("config", configFieldSet)} creates a direct nested
     *   structure</li>
     *   <li>Nested path: {@code put("parent.child", nestedFieldSet)} creates a nested
     *   hierarchy</li>
     * </ul>
     *
     * @param key the key with which to associate the nested SimpleFieldSet. Cannot be null
     * @param fs  the SimpleFieldSet to be associated with the key. Cannot be null
     *
     * @see #tput(String, SimpleFieldSet)
     */
    public void put(String key, SimpleFieldSet fs) {
        if (fs == null) {
            return; // legal no-op, because used everywhere
        }
        if (fs.isEmpty()) // can't just no-op, because caller might add the
        // FS then populate it...
        {
            throw new IllegalArgumentException(
                "Cannot add empty SimpleFieldSet for key: " + key);
        }
        if (subsets == null) {
            subsets = HashMap.newHashMap(4);
        }
        if (subsets.containsKey(key)) {
            throw new IllegalArgumentException(
                String.format("Duplicate key '%s': SimpleFieldSet already exists", key));
        }
        subsets.put(key, fs);
    }

    /**
     * Removes a value or subset associated with the specified key from this SimpleFieldSet.
     *
     * <p>This method supports both direct key-value pairs and nested hierarchical keys
     * separated by {@link #MULTI_LEVEL_CHAR}. If the key refers to a nested value, it
     * traverses the hierarchy to remove the specified entry.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Direct key: {@code removeValue("key1")} removes the value associated with
     *   "key1"</li>
     *   <li>Nested key: {@code removeValue("parent.child")} removes the nested value or
     *   subset</li>
     * </ul>
     *
     * <p>The method is thread-safe through synchronization.</p>
     *
     * @param key the key whose associated value or subset is to be removed. Cannot be null
     *
     * @see #removeSubset(String)
     */
    public synchronized void removeValue(String key) {
        int separatorIndex = key.indexOf(MULTI_LEVEL_CHAR);
        if (separatorIndex == -1) {
            values.remove(key);
            return;
        }

        if (subsets == null) {
            return;
        }

        String parentKey = key.substring(0, separatorIndex);
        String childKey = key.substring(separatorIndex + 1);

        SimpleFieldSet childSet = subsets.get(parentKey);
        if (childSet == null) {
            return;
        }
        childSet.removeValue(childKey);

        if (!childSet.isEmpty()) {
            return;
        }

        subsets.remove(parentKey);
        if (subsets.isEmpty()) {
            subsets = null;
        }
    }

    /**
     * Removes a nested subset and all its child entries from this SimpleFieldSet.
     *
     * <p>This method removes the specified subset and all its nested children from the
     * hierarchy. It supports both direct subset names and hierarchical keys separated by
     * {@link #MULTI_LEVEL_CHAR}.</p>
     *
     * <p><b>Example structure before removal:</b></p>
     * <pre>
     * foo=bar
     * foo.bar=foobar
     * foo.bar.boo=foobarboo
     * other=value
     * </pre>
     *
     * <p>After calling {@code removeSubset("foo")}, the structure becomes:</p>
     * <pre>
     * foo=bar
     * other=value
     * </pre>
     *
     * <p>The method is thread-safe through synchronization.</p>
     *
     * @param key the key identifying the subset to remove. Cannot be null
     *
     * @see #removeValue(String)
     */
    public synchronized void removeSubset(String key) {
        if (subsets == null) {
            return;
        }

        int separatorIndex = key.indexOf(MULTI_LEVEL_CHAR);
        if (separatorIndex == -1) {
            subsets.remove(key);
            if (subsets.isEmpty()) {
                subsets = null;
            }
            return;
        }

        String parentKey = key.substring(0, separatorIndex);
        SimpleFieldSet childSet = subsets.get(parentKey);
        if (childSet == null) {
            return;
        }

        childSet.removeSubset(key.substring(separatorIndex + 1));
        if (childSet.isEmpty()) {
            subsets.remove(parentKey);
            if (subsets.isEmpty()) {
                subsets = null;
            }
        }
    }

    /**
     * Checks if this SimpleFieldSet contains no entries.
     *
     * <p>A SimpleFieldSet is considered empty if it has no direct key-value pairs
     * and no nested subsets. The method is thread-safe through synchronization.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Empty SimpleFieldSet: returns {@code true}</li>
     *   <li>SimpleFieldSet with only direct values: returns {@code false}</li>
     *   <li>SimpleFieldSet with only subsets: returns {@code false}</li>
     * </ul>
     *
     * @return {@code true} if this SimpleFieldSet has no entries, {@code false} otherwise
     */
    public synchronized boolean isEmpty() {
        return values.isEmpty() && (subsets == null || subsets.isEmpty());
    }

    /**
     * Returns an iterator over the names of all direct nested subsets in this SimpleFieldSet.
     *
     * <p>This method returns only the immediate subset names at the current level,
     * excluding any deeper nested structures.</p>
     *
     * <p><b>Example for this structure:</b></p>
     * <pre>
     * key1=value1
     * config.setting1=value2
     * config.setting2=value3
     * deep.nested.key=value4
     * </pre>
     *
     * <p>The iterator will return only:</p>
     * <ul>
     *   <li>{@code config}</li>
     *   <li>{@code deep}</li>
     * </ul>
     *
     * @return an iterator over the names of direct nested subsets in this SimpleFieldSet.
     * Returns an empty iterator if there are no nested subsets
     */
    public Iterator<String> directSubsetNameIterator() {
        return (subsets == null) ? null : subsets.keySet().iterator();
    }

    /**
     * Returns an array of names of all direct nested subsets in this SimpleFieldSet.
     *
     * <p>This method returns only the immediate subset names at the current level,
     * excluding any deeper nested structures. The returned array maintains the order of the
     * underlying collection.</p>
     *
     * <p><b>Example for this structure:</b></p>
     * <pre>
     * key1=value1
     * config.setting1=value2
     * config.setting2=value3
     * deep.nested.key=value4
     * </pre>
     *
     * <p>The returned array will contain only:</p>
     * <ul>
     *   <li>{@code config}</li>
     *   <li>{@code deep}</li>
     * </ul>
     *
     * @return an array containing the names of all direct nested subsets. Returns an empty an
     * empty array if there are no nested subsets
     */
    public String[] namesOfDirectSubsets() {
        return (subsets == null) ? EMPTY_STRING_ARRAY :
            subsets.keySet().toArray(EMPTY_STRING_ARRAY);
    }

    /**
     * Writes the complete field set to an output stream using UTF-8 encoding.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets. The output is written using UTF-8 character encoding and follows the
     * standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>Values that require Base64 encoding (based on the {@code alwaysUseBase64} setting)
     * can be automatically encoded during writing.</p>
     *
     * @param os the output stream to which the field set will be written. Cannot be null
     *
     * @throws IOException if an error occurs while writing to the output stream
     * @see #writeToOrdered(Writer)
     * @see #writeToBigBuffer(OutputStream)
     */
    public void writeTo(OutputStream os) throws IOException {
        writeTo(os, 4096);
    }

    /**
     * Writes the complete field set to an output stream using a large buffer for improved
     * performance. It's for jobs that aren't called too often. e.g. persisting a file every 10
     * minutes.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets, using a buffered writer with an 8KB buffer. The output is written
     * using UTF-8 character encoding and follows the standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>Values that require Base64 encoding (based on the {@code alwaysUseBase64} setting)
     * can be automatically encoded during writing.</p>
     *
     * @param os the output stream to which the field set will be written. Cannot be null
     *
     * @throws IOException if an error occurs while writing to the output stream
     * @see #writeTo(OutputStream)
     */
    public void writeToBigBuffer(OutputStream os) throws IOException {
        writeTo(os, 65536);
    }

    /**
     * Writes the complete field set to an output stream using a specified buffer size.
     *
     * <p>This method serializes the entire field set, including headers, direct values,
     * and nested subsets, using a buffered writer with the specified buffer size. The output
     * is written using UTF-8 character encoding and follows the standard structure:</p>
     *
     * <pre>
     * # Optional header comments
     * key1=value1
     * key2=value2
     * nested.key=value3
     * End
     * </pre>
     *
     * <p>Values that require Base64 encoding (based on the {@code alwaysUseBase64} setting)
     * can be automatically encoded during writing.</p>
     *
     * @param os         the output stream to which the field set will be written. Cannot be
     *                   null
     * @param bufferSize the size of the buffer to use for writing. Must be greater than zero
     *
     * @throws IOException if an error occurs while writing to the output stream
     * @see #writeTo(OutputStream)
     */
    public void writeTo(OutputStream os, int bufferSize) throws IOException {
        try (var bos = new BufferedOutputStream(os, bufferSize);
             var osw = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
             var bw = new BufferedWriter(osw)) {
            writeTo(bw);
        }
    }

    /**
     * Get an integer value for the given key. This may be at the top level or lower in the
     * tree, it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     * @param def The default value to return if the key does not exist or can't be parsed.
     *
     * @return The integer value of the key, or the default value.
     */
    public int getInt(String key, int def) {
        String s = get(key);
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Get an integer value for the given key. This may be at the top level or lower in the
     * tree, it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     *
     * @return The integer value of the key, if it exists and is valid.
     *
     * @throws FSParseException If the key=value pair does not exist or if the value cannot be
     *                          parsed as an integer.
     */
    public int getInt(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No integer key " + key);
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new FSParseException("Cannot parse integer " + s + " for " + key);
        }
    }

    /**
     * Get a double precision value for the given key. This may be at the top level or lower in
     * the tree, it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     * @param def The default value to return if the key does not exist or can't be parsed.
     *
     * @return The integer value of the key, or the default value.
     */
    public double getDouble(String key, double def) {
        String s = get(key);
        if (s == null) {
            return def;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Get a double precision value for the given key. This may be at the top level or lower in
     * the tree, it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     *
     * @return The value of the key as a double, if it exists and is valid.
     *
     * @throws FSParseException If the key=value pair does not exist or if the value cannot be
     *                          parsed as a double.
     */
    public double getDouble(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No double key " + key);
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new FSParseException("Cannot parse double " + s + " for " + key);
        }
    }

    /**
     * Get a long value for the given key. This may be at the top level or lower in the tree,
     * it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     * @param def The default value to return if the key does not exist or can't be parsed.
     *
     * @return The long value of the key, or the default value.
     */
    public long getLong(String key, long def) {
        String s = get(key);
        if (s == null) {
            return def;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Get a long value for the given key. This may be at the top level or lower in the tree,
     * it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     *
     * @return The value of the key as a long, if it exists and is valid.
     *
     * @throws FSParseException If the key=value pair does not exist or if the value cannot be
     *                          parsed as a long.
     */
    public long getLong(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No long key " + key);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new FSParseException("Cannot parse long " + s + " for " + key);
        }
    }

    /**
     * Get a short value for the given key. This may be at the top level or lower in the tree,
     * it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     *
     * @return The value of the key as a short, if it exists and is valid.
     *
     * @throws FSParseException If the key=value pair does not exist or if the value cannot be
     *                          parsed as a short.
     */
    public short getShort(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No short key " + key);
        }
        try {
            return Short.parseShort(s);
        } catch (NumberFormatException e) {
            throw new FSParseException("Cannot parse short " + s + " for " + key);
        }
    }

    /**
     * Get a short value for the given key. This may be at the top level or lower in the tree,
     * it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     *
     * @return The value of the key as a short, if it exists and is valid.
     */
    public short getShort(String key, short def) {
        String s = get(key);
        if (s == null) {
            return def;
        }
        try {
            return Short.parseShort(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Get a byte value for the given key (represented as a number in decimal). This may be at
     * the top level or lower in the tree, it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     *
     * @return The value of the key as a byte, if it exists and is valid.
     *
     * @throws FSParseException If the key=value pair does not exist or if the value cannot be
     *                          parsed as a byte.
     */
    public byte getByte(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No byte key " + key);
        }
        try {
            return Byte.parseByte(s);
        } catch (NumberFormatException e) {
            throw new FSParseException("Cannot parse byte " + s + ".");
        }
    }

    /**
     * Get a byte value for the given key (represented as a number in decimal). This may be at
     * the top level or lower in the tree, it's just key=value. (Value in decimal)
     *
     * @param key The key to fetch.
     *
     * @return The value of the key as a byte, if it exists and is valid, otherwise the default
     * value.
     */
    public byte getByte(String key, byte def) {
        try {
            return getByte(key);
        } catch (FSParseException e) {
            return def;
        }
    }

    /**
     * Get a byte array for the given key (represented in Base64). The key may be at the top
     * level or further down the tree, so this is key=[base64 of value].
     *
     * @param key The key to fetch.
     *
     * @return The byte array to fetch.
     *
     * @throws FSParseException If the key does not exist or cannot be parsed as a byte array.
     */
    public byte[] getByteArray(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No key " + key);
        }
        try {
            return hyphanet.support.Base64.decode(s);
        } catch (IllegalBase64Exception e) {
            throw new FSParseException("Cannot parse value \"" + s + "\" as a byte[]");
        }
    }

    /**
     * Get a char for the given key (represented as a single character). The key may be at the
     * top level or further down the tree, so this is key=[character].
     *
     * @param key The key to fetch.
     *
     * @return The character to fetch.
     *
     * @throws FSParseException If the key does not exist or there is more than one character.
     */
    public char getChar(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No key " + key);
        }
        if (s.length() == 1) {
            return s.charAt(0);
        } else {
            throw new FSParseException("Cannot parse " + s + " for char " + key);
        }
    }

    /**
     * Get a char for the given key (represented as a single character). The key may be at the
     * top level or further down the tree, so this is key=[character].
     *
     * @param key The key to fetch.
     * @param def The default value to return if the key does not exist or can't be parsed.
     *
     * @return The character to fetch.
     */
    public char getChar(String key, char def) {
        String s = get(key);
        if (s == null) {
            return def;
        }
        if (s.length() == 1) {
            return s.charAt(0);
        } else {
            return def;
        }
    }

    public boolean getBoolean(String key, boolean def) {
        return Fields.stringToBool(get(key), def);
    }

    public boolean getBoolean(String key) throws FSParseException {
        try {
            return Fields.stringToBool(get(key));
        } catch (NumberFormatException e) {
            throw new FSParseException(e);
        }
    }

    public void put(String key, int[] value) {
        putArray(key, ArrayUtils.toObject(value));
    }

    public void put(String key, double[] value) {
        putArray(key, ArrayUtils.toObject(value));
    }

    public void put(String key, float[] value) {
        putArray(key, ArrayUtils.toObject(value));
    }

    public void put(String key, short[] value) {
        putArray(key, ArrayUtils.toObject(value));
    }

    public void put(String key, long[] value) {
        putArray(key, ArrayUtils.toObject(value));
    }

    public void put(String key, boolean[] value) {
        putArray(key, ArrayUtils.toObject(value));
    }

    public int[] getIntArray(String key) {
        Integer[] result = getPrimitiveArray(key, Integer.class, Integer::parseInt);
        return ArrayUtils.toPrimitive(result);
    }

    public long[] getLongArray(String key) {
        Long[] result = getPrimitiveArray(key, Long.class, Long::parseLong);
        return ArrayUtils.toPrimitive(result);
    }

    public double[] getDoubleArray(String key) {
        Double[] result = getPrimitiveArray(key, Double.class, Double::parseDouble);
        return ArrayUtils.toPrimitive(result);
    }

    public float[] getFloatArray(String key) {
        Float[] result = getPrimitiveArray(key, Float.class, Float::parseFloat);
        return ArrayUtils.toPrimitive(result);
    }

    public boolean[] getBooleanArray(String key) {
        Boolean[] result = getPrimitiveArray(key, Boolean.class, Boolean::parseBoolean);
        return ArrayUtils.toPrimitive(result);
    }

    public short[] getShortArray(String key) {
        Short[] result = getPrimitiveArray(key, Short.class, Short::parseShort);
        return ArrayUtils.toPrimitive(result);
    }

    public void putOverwrite(String key, String[] strings) {
        putOverwrite(key, unsplit(strings));
    }

    public void putEncoded(String key, final String[] strings) {
        if (strings == null || strings.length == 0) {
            putSingle(key, "");
            return;
        }

        String[] encodedStrings = new String[strings.length];
        for (int i = 0; i < strings.length; i++) {
            encodedStrings[i] = Base64.encodeUTF8(strings[i]);
        }
        putSingle(key, unsplit(encodedStrings));
    }

    public String getString(String key) throws FSParseException {
        String s = get(key);
        if (s == null) {
            throw new FSParseException("No such element " + key);
        }
        return s;
    }

    /**
     * Get the headers. This is a list of String's that are written before the name=value
     * pairs. Usually this is a comment (with each line starting with "#").
     */
    public String[] getHeader() {
        return header;
    }

    /**
     * Set the headers. This is a list of String's that are written before the name=value
     * pairs. Usually this is a comment (with each line starting with "#").
     *
     * @param headers The list of lines to precede the SimpleFieldSet by when we write it.
     */
    public void setHeader(String... headers) {
        // FIXME LOW should really check that each line doesn't have a "\n"
        //  in it
        this.header = headers;
    }

    public void put(String key, String[] values) {
        putSingle(key, unsplit(values));
    }

    /**
     * Write the contents of the SimpleFieldSet to a Writer. Note: The caller *must* buffer the
     * writer to avoid lousy performance! (StringWriter is by definition buffered, otherwise
     * wrap it in a BufferedWriter)
     *
     * @param w           The Writer to write to. @warning keep in mind that a Writer is not
     *                    necessarily UTF-8!!
     * @param prefix      String to prefix the keys with. (E.g. when writing a tree of SFS's).
     * @param noEndMarker If true, don't write the end marker (the last line, the only one with
     *                    no "=" in it).
     * @param useBase64   If true, use Base64 for any value that has control characters,
     *                    whitespace, or characters used by SimpleFieldSet in it. In this case
     *                    the separator will be "==" not "=". This is mainly useful for node
     *                    references, which tend to lose whitespace, gain newlines etc in
     *                    transit. Can be overridden (to true) by alwaysUseBase64 setting.
     */
    synchronized void writeTo(Writer w, String prefix, boolean noEndMarker, boolean useBase64)
        throws IOException {
        writeHeader(w);

        // Write direct values
        values.forEach(Exceptions.sneak().fromBiConsumer(
            (key, value) -> writeValue(w, key, value, prefix, useBase64)));

        // Write nested subsets
        if (subsets != null) {
            subsets.forEach(Exceptions.sneak().fromBiConsumer((key, subset) -> {
                if (subset == null) {
                    throw new NullPointerException("Subset cannot be null for key: " + key);
                }
                subset.writeTo(w, prefix + key + MULTI_LEVEL_CHAR, true, useBase64);
            }));
        }

        // Write end marker if needed
        if (!noEndMarker) {
            w.write(endMarker == null ? "End\n" : endMarker + "\n");
        }
    }

    private <T> void putArray(String key, T[] array) {
        removeValue(key);
        for (T value : array) {
            putAppend(key, StringUtils.defaultString(String.valueOf(value)));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T[] getPrimitiveArray(String key, Class<T> type, ArrayConverter<T> converter) {
        String[] strings = getAll(key);
        if (strings == null || strings.length == 0) {
            return (T[]) java.lang.reflect.Array.newInstance(type, 0);
        }

        try {
            T[] result = (T[]) java.lang.reflect.Array.newInstance(type, strings.length);
            for (int i = 0; i < strings.length; i++) {
                result[i] = converter.convert(strings[i]);
            }
            return result;
        } catch (NumberFormatException e) {
            logger.error("Failed to parse {} array for key '{}': {}", type.getSimpleName(),
                         key, e.getMessage());
            return (T[]) java.lang.reflect.Array.newInstance(type, 0);
        }
    }

    /**
     * Combine a list of String's into a single String, separating them by the
     * MULTI_VALUE_CHAR.
     */
    private static String unsplit(String @Nullable [] strings) {
        if (strings == null || strings.length == 0) {
            return "";
        }

        // Validate no strings contain the separator
        for (String s : strings) {
            if (s.indexOf(MULTI_VALUE_CHAR) != -1) {
                throw new IllegalArgumentException(
                    "String contains illegal separator character: " + s);
            }
        }

        return String.join(String.valueOf(MULTI_VALUE_CHAR), strings);
    }

    /**
     * @see #read(LineReader, int, int, boolean, boolean)
     */
    private void read(LineReader lr, boolean allowMultiple, boolean allowBase64)
        throws IOException {
        read(lr, Integer.MAX_VALUE, 0x100, true, allowMultiple, allowBase64);
    }

    /**
     * Read from stream. Format:
     * <p>
     * # Header1 # Header2 key0=val0 key1=val1 # comment key2=val2 End
     * <p>
     * (headers and comments are optional)
     *
     * @param utfOrIso88591 If true, read as UTF-8, otherwise read as ISO-8859-1.
     */
    private void read(
        LineReader br, int maxLength, int bufferSize, boolean utfOrIso88591,
        boolean allowMultiple, boolean allowBase64) throws IOException {
        List<String> headers = new ArrayList<>();
        boolean headerSection = true;
        String line = br.readLine(maxLength, bufferSize, utfOrIso88591);

        if (line == null) {
            throw new EOFException("Empty input stream");
        }

        while (line != null) {
            if (!line.isEmpty()) {
                ProcessLineResult result =
                    processLine(line, headers, headerSection, allowMultiple, allowBase64);
                if (result.endReached()) {
                    break;
                }
                headerSection = result.headerSection();
            }
            line = br.readLine(maxLength, bufferSize, utfOrIso88591);
        }

        if (endMarker == null) {
            logger.error("No end marker found in input");
        }
    }

    private ProcessLineResult processLine(
        String line, List<String> headers, boolean headerSection, boolean allowMultiple,
        boolean allowBase64) throws IOException {
        if (line.charAt(0) == '#') {
            if (headerSection) {
                headers.add(line.substring(1).trim());
            }
            return new ProcessLineResult(true, headerSection);
        } else {
            if (headerSection && !headers.isEmpty()) {
                header = headers.toArray(String[]::new);
            }

            int separatorIndex = line.indexOf(KEYVALUE_SEPARATOR_CHAR);
            if (separatorIndex >= 0) {
                processKeyValuePair(line, separatorIndex, allowBase64, allowMultiple);
                return new ProcessLineResult(false, false);
            } else {
                endMarker = line;
                return new ProcessLineResult(true, false);
            }
        }
    }

    private void processKeyValuePair(
        String line, int separatorIndex, boolean allowBase64,
        boolean allowMultiple) throws IOException {
        String key = line.substring(0, separatorIndex).trim();
        String value = line.substring(separatorIndex + 1);

        if (!value.isEmpty() && value.charAt(0) == '=' && allowBase64) {
            try {
                value = Base64.decodeUTF8(value.substring(1).replaceAll("\\s", ""));
            } catch (IllegalBase64Exception e) {
                throw new IOException("Invalid Base64 encoding in value for key: " + key, e);
            }
        }

        put(key, value, allowMultiple, false, true);
    }

    /**
     * Set a key to a value.
     *
     * @param key           The key.
     * @param value         The value.
     * @param allowMultiple If true, if the key already exists then the value will be appended
     *                      to the existing value. If false, we return false to indicate that
     *                      the old value is unchanged.
     *
     * @return True unless allowMultiple was false and there was a pre-existing value, or value
     * was null.
     */
    private synchronized boolean put(
        String key, String value, boolean allowMultiple,
        boolean overwrite, boolean fromRead) {
        if (value == null) {
            return true;
        }

        validateValue(value, allowMultiple, fromRead);

        int separatorIndex = key.indexOf(MULTI_LEVEL_CHAR);
        if (separatorIndex == -1) {
            return putDirectValue(key, value, allowMultiple, overwrite);
        }

        return putNestedValue(key, value, separatorIndex, allowMultiple, overwrite, fromRead);
    }

    private void validateValue(String value, boolean allowMultiple, boolean fromRead) {
        if ((!alwaysUseBase64) && value.indexOf('\n') != -1) {
            throw new IllegalArgumentException("SimpleFieldSet cannot accept newlines");
        }
        if (allowMultiple && (!fromRead) && value.indexOf(MULTI_VALUE_CHAR) != -1) {
            throw new IllegalArgumentException(
                String.format("Value contains illegal multi-value character '%c': %s",
                              MULTI_VALUE_CHAR, value));
        }
    }

    private boolean putDirectValue(
        String key, String value, boolean allowMultiple, boolean overwrite) {
        if (overwrite) {
            values.put(key, value);
            return true;
        }

        String existingValue = values.putIfAbsent(key, value);
        if (existingValue == null) {
            return true;
        }

        if (!allowMultiple) {
            return false;
        }

        values.put(key, existingValue + MULTI_VALUE_CHAR + value);
        return true;
    }

    private boolean putNestedValue(
        String key, String value, int separatorIndex, boolean allowMultiple, boolean overwrite,
        boolean fromRead) {
        String before = key.substring(0, separatorIndex);
        String after = key.substring(separatorIndex + 1);

        if (subsets == null) {
            subsets = new HashMap<>();
        }

        SimpleFieldSet fs =
            subsets.computeIfAbsent(before, k -> new SimpleFieldSet(alwaysUseBase64));

        return fs.put(after, value, allowMultiple, overwrite, fromRead);
    }

    private void writeValue(
        Writer w, String key, String value, String prefix, boolean useBase64)
        throws IOException {
        // Validate inputs
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        // Use StringBuilder for better performance
        var lineBuilder = new StringBuilder(prefix != null ? prefix : "").append(key).append(
            KEYVALUE_SEPARATOR_CHAR);

        // Handle value encoding
        if ((useBase64 || alwaysUseBase64) && shouldBase64(value)) {
            lineBuilder.append(KEYVALUE_SEPARATOR_CHAR).append(Base64.encodeUTF8(value));
        } else {
            lineBuilder.append(value);
        }

        // Write the complete line
        w.write(lineBuilder.append('\n').toString());

    }

    private boolean shouldBase64(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        return value.chars().anyMatch(this::isSpecialCharacter);
    }

    private boolean isSpecialCharacter(int c) {
        return c == SimpleFieldSet.KEYVALUE_SEPARATOR_CHAR ||
               c == SimpleFieldSet.MULTI_LEVEL_CHAR || c == SimpleFieldSet.MULTI_VALUE_CHAR ||
               Character.isISOControl(c) || Character.isWhitespace(c);
    }

    /**
     * Write the SimpleFieldSet to a Writer, in order.
     *
     * @param w                   The Writer to write the SFS to.
     * @param prefix              The prefix ("" at the top level, e.g. "something." if we are
     *                            inside a sub-SFS called "something").
     * @param noEndMarker         If true, don't write the end marker (usually End). Again this
     *                            is normally set only in sub-SFS's.
     * @param allowOptionalBase64 If true, write fields as Base64 if they contain spaces etc.
     *                            This improves the robustness of e.g. node references, where
     *                            the SFS can be written with or without Base64. However, for
     *                            SFS's where the values can contain <b>anything</b>, the
     *                            member flag alwaysUseBase64 will be set and we will write
     *                            lines that need to be Base64 as such regardless of this
     *                            allowOptionalBase64.
     *
     * @throws IOException If an error occurs writing to the Writer.
     */
    private synchronized void writeToOrdered(
        Writer w, String prefix, boolean noEndMarker,
        boolean allowOptionalBase64) throws IOException {

        writeHeader(w);

        // Handle direct values
        values.keySet().stream().sorted().forEach(Exceptions.sneak().consumer(
            key -> writeValue(w, key, get(key), prefix, allowOptionalBase64)));

        // Handle subsets
        if (subsets != null && !subsets.isEmpty()) {
            subsets.keySet().stream().sorted()
                   .forEach(Exceptions.sneak().consumer(prefixKey -> {
                       SimpleFieldSet subset = subset(prefixKey);
                       if (subset == null) {
                           throw new IllegalStateException(
                               "Subset cannot be null for key: " + prefixKey);
                       }
                       var newPrefix = prefix + prefixKey + MULTI_LEVEL_CHAR;
                       subset.writeToOrdered(w, newPrefix, true, allowOptionalBase64);
                   }));
        }

        // Write end marker
        if (!noEndMarker) {
            w.write(endMarker == null ? "End\n" : endMarker + '\n');
        }
    }

    private void writeHeader(Writer w) throws IOException {
        if (header != null) {
            var headerBuilder = new StringBuilder();
            for (String line : header) {
                headerBuilder.append("# ").append(line).append('\n');
            }
            w.write(headerBuilder.toString());
        }
    }

    @FunctionalInterface
    private interface ArrayConverter<T> {
        T convert(String value) throws NumberFormatException;
    }

    private record ProcessLineResult(boolean endReached, boolean headerSection) {
    }

    /**
     * Map storing direct key-value pairs for this field set. Keys are strings without any
     * {@value #MULTI_LEVEL_CHAR} characters.
     */
    private final Map<String, String> values;

    /**
     * Flag indicating whether Base64 encoding should always be used for values. When true, all
     * values that require encoding will be Base64-encoded, regardless of the
     * allowOptionalBase64 parameter.
     */
    private final boolean alwaysUseBase64;

    /**
     * Optional header lines to be written at the beginning of the serialized output. Each
     * string represents one header line that will be prefixed with "# " when written.
     */
    protected @Nullable String[] header;

    /**
     * Map storing nested SimpleFieldSet instances. Keys represent the prefix for the nested
     * set, values are the corresponding SimpleFieldSet objects. May be null if there are no
     * nested subsets.
     */
    private @Nullable Map<String, SimpleFieldSet> subsets;

    /**
     * Custom end marker for serialization. If null, the default "End" marker will be used when
     * writing the field set.
     */
    private @Nullable String endMarker;
}
