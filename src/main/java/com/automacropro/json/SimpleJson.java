package com.automacropro.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer used to save and load {@code .amacro} project
 * files and the app's settings file.
 *
 * Deliberately hand-rolled instead of depending on Gson/Jackson: the data
 * model here is simple (Maps, Lists, Strings, Numbers, Booleans, null), and
 * avoiding a third-party JSON library keeps the only external dependency in
 * this whole project down to JNativeHook - one less jar to manage when
 * building the .exe with jpackage/Launch4j.
 */
public final class SimpleJson {

    private SimpleJson() {
    }

    // ----------------------------------------------------------------- write

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb, indent);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb, indent);
        } else {
            // Fallback: never throw on an unexpected type, just stringify it.
            writeString(value.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> obj, StringBuilder sb, int indent) {
        if (obj.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            indent(sb, indent + 1);
            writeString(e.getKey(), sb);
            sb.append(": ");
            writeValue(e.getValue(), sb, indent + 1);
            if (++i < obj.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(List<Object> arr, StringBuilder sb, int indent) {
        if (arr.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < arr.size(); i++) {
            indent(sb, indent + 1);
            writeValue(arr.get(i), sb, indent + 1);
            if (i < arr.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
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

    // ------------------------------------------------------------------ read

    /**
     * Parses a JSON document into plain Java objects: {@code Map<String,Object>}
     * for objects, {@code List<Object>} for arrays, {@code String}, {@code Double},
     * {@code Boolean}, or {@code null}.
     */
    public static Object parse(String json) {
        Parser p = new Parser(json);
        Object result = p.parseValue();
        p.skipWhitespace();
        if (!p.isAtEnd()) {
            throw new JsonParseException("Karakter tak terduga setelah nilai JSON pada posisi " + p.pos);
        }
        return result;
    }

    public static class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean isAtEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (pos >= s.length()) {
                throw new JsonParseException("JSON berakhir lebih cepat dari yang diharapkan");
            }
            return s.charAt(pos);
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return parseNumber();
            }
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (peek() != ':') {
                    throw new JsonParseException("Mengharapkan ':' pada posisi " + pos);
                }
                pos++;
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw new JsonParseException("Mengharapkan ',' atau '}' pada posisi " + pos);
                }
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    break;
                } else {
                    throw new JsonParseException("Mengharapkan ',' atau ']' pada posisi " + pos);
                }
            }
            return list;
        }

        String parseString() {
            if (peek() != '"') {
                throw new JsonParseException("Mengharapkan string pada posisi " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            throw new JsonParseException("Escape tidak dikenal: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            boolean isDouble = false;
            if (pos < s.length() && s.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isDouble = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = s.substring(start, pos);
            if (numStr.isEmpty() || numStr.equals("-")) {
                throw new JsonParseException("Angka tidak valid pada posisi " + start);
            }
            return isDouble ? (Object) Double.parseDouble(numStr) : (Object) Long.parseLong(numStr);
        }

        void expect(String literal) {
            if (pos + literal.length() > s.length() || !s.substring(pos, pos + literal.length()).equals(literal)) {
                throw new JsonParseException("Mengharapkan literal '" + literal + "' pada posisi " + pos);
            }
            pos += literal.length();
        }
    }
}
