package jvbot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal zero-dependency JSON reader/writer.
 *
 * write()  : Map / List / String / Number / Boolean / null  ->  JSON text
 * parse()  : JSON text  ->  Map&lt;String,Object&gt; / List&lt;Object&gt; / String / Double / Boolean / null
 * get()    : safe navigation helper, e.g. Json.get(root, "choices", 0, "delta", "content")
 */
public final class Json {

    private Json() {}

    // ---------------------------------------------------------------- writer

    /** Serialize a value to compact JSON. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, -1, 0);
        return sb.toString();
    }

    /** Serialize a value to indented, human-readable JSON (used for chat files). */
    public static String writePretty(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 2, 0);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v, int indent, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            writeString(sb, (String) v);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v.toString());
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append((long) d);          // print 3 instead of 3.0
            } else {
                sb.append(d);
            }
        } else if (v instanceof Map) {
            writeMap(sb, (Map<?, ?>) v, indent, depth);
        } else if (v instanceof List) {
            writeList(sb, (List<?>) v, indent, depth);
        } else {
            writeString(sb, v.toString());
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int indent, int depth) {
        if (map.isEmpty()) { sb.append("{}"); return; }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            newline(sb, indent, depth + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            if (indent >= 0) sb.append(' ');
            writeValue(sb, e.getValue(), indent, depth + 1);
        }
        newline(sb, indent, depth);
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, List<?> list, int indent, int depth) {
        if (list.isEmpty()) { sb.append("[]"); return; }
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            first = false;
            newline(sb, indent, depth + 1);
            writeValue(sb, item, indent, depth + 1);
        }
        newline(sb, indent, depth);
        sb.append(']');
    }

    private static void newline(StringBuilder sb, int indent, int depth) {
        if (indent < 0) return;
        sb.append('\n');
        for (int i = 0; i < indent * depth; i++) sb.append(' ');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- parser

    /** Parse JSON text into Map / List / String / Double / Boolean / null. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd()) throw new IllegalArgumentException("Unexpected trailing JSON at position " + p.pos);
        return value;
    }

    /**
     * Walk a parsed tree safely: string keys index into Maps, integers into Lists.
     * Returns null if any step is missing or of the wrong type.
     */
    @SuppressWarnings("unchecked")
    public static Object get(Object root, Object... path) {
        Object cur = root;
        for (Object key : path) {
            if (cur instanceof Map && key instanceof String) {
                cur = ((Map<String, Object>) cur).get(key);
            } else if (cur instanceof List && key instanceof Integer) {
                List<Object> list = (List<Object>) cur;
                int i = (Integer) key;
                cur = (i >= 0 && i < list.size()) ? list.get(i) : null;
            } else {
                return null;
            }
        }
        return cur;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (atEnd()) throw new IllegalArgumentException("Unexpected end of JSON");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (atEnd() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expectWord("true");  return Boolean.TRUE;
                case 'f': expectWord("false"); return Boolean.FALSE;
                case 'n': expectWord("null");  return null;
                default:  return parseNumber();
            }
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw new IllegalArgumentException("Unterminated string");
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (atEnd()) throw new IllegalArgumentException("Unterminated escape");
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'u':
                        if (pos + 4 > s.length()) throw new IllegalArgumentException("Bad \\u escape");
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Bad escape '\\" + e + "' at position " + pos);
                }
            }
        }

        Double parseNumber() {
            int start = pos;
            if (!atEnd() && (peek() == '-' || peek() == '+')) pos++;
            while (!atEnd()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') pos++;
                else break;
            }
            if (pos == start) throw new IllegalArgumentException("Invalid JSON value at position " + pos);
            try {
                return Double.parseDouble(s.substring(start, pos));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid number at position " + start);
            }
        }

        void expectWord(String word) {
            if (!s.startsWith(word, pos)) {
                throw new IllegalArgumentException("Invalid JSON value at position " + pos);
            }
            pos += word.length();
        }
    }
}
