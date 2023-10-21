package io.github.amelonrind.storagebrowser.data;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

public class SearchSession {
    private int syncId = 0;
    private boolean queryEmpty = true;
    private final Map<Type, Map<String, WeakReference<Token>>> tokens = new HashMap<>(6, 1.0f);
    private final Map<Type, Token> queries = new HashMap<>(6, 1.0f);
    private transient final Map<Type, List<String>> queryCache = new HashMap<>(6, 1.0f);

    public void setupTokens(Map<Type, Token> map, String name, String tooltip, String modid, String id, String tags) {
        StringBuilder generic = new StringBuilder(name);
        for (String w : id.split("\\W")) {
            if (!w.isBlank() && !generic.toString().contains(w)) {
                generic.append(" ").append(w);
            }
        }
        map.put(Type.GENERIC, of(generic.toString(), Type.GENERIC));
        map.put(Type.TOOLTIP, of(tooltip, Type.TOOLTIP));
        map.put(Type.TAG, of(tags, Type.TAG));
        map.put(Type.IDENTIFIER, of(id, Type.IDENTIFIER));
        map.put(Type.MODID, of(modid, Type.MODID));
    }

    public SearchSession() {
        for (Type t : Type.values()) {
            tokens.put(t, new HashMap<>());
            queryCache.put(t, new ArrayList<>());
        }
    }

    public void setQuery(@NotNull String query) {
        queryCache.values().forEach(List::clear);
        for (String word : query.split("\\s")) if (!word.isBlank()) {
            switch (word.charAt(0)) {
                case '#': queryCache.get(Type.TOOLTIP).add(word.substring(1)); break;
                case '$': queryCache.get(Type.TAG).add(word.substring(1)); break;
                case '*': queryCache.get(Type.IDENTIFIER).add(word.substring(1)); break;
                case '@': queryCache.get(Type.MODID).add(word.substring(1)); break;
                case '\\': queryCache.get(Type.GENERIC).add(word.substring(1)); break;
                default: queryCache.get(Type.GENERIC).add(word);
            }
        }
        boolean empty = true;
        boolean anyChanged = false;
        for (Type t : Type.values()) {
            List<String> queryWords = queryCache.get(t);
            Token prev = queries.get(t);
            String reduced;
            if (queryWords.isEmpty() || (reduced = queryWords.stream().reduce("", (a, b) -> a + " " + b)).isBlank()) {
                if (prev != null) anyChanged = true;
                queries.put(t, null);
                continue;
            }
            empty = false;
            Token token = of(reduced.trim(), t);
            if (!token.equals(prev)) {
                anyChanged = true;
                queries.put(t, token);
            }
        }
        queryEmpty = empty;
        if (anyChanged) syncId++;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isQueryEmpty() {
        return queryEmpty;
    }

    public Token of(String text, Type type) {
        Token token;
        text = text.trim().toLowerCase();
        Map<String, WeakReference<Token>> map = tokens.get(type);
        WeakReference<Token> ref = map.get(text);
        if (ref != null) {
            token = ref.get();
            if (token != null) return token;
        }
        token = new Token(this, type, text);
        map.put(text, new WeakReference<>(token));
        return token;
    }

    public static class Token implements Comparable<Token> {
        private final SearchSession parent;
        private final Type type;
        private final String text;
        private final int lengthWithoutWhitespace;
        private final int[] codePoints;
        private final Int2IntMap codes = new Int2IntAVLTreeMap();

        private int syncId = -1;
        private int score = -1; // lower better, -1 for null

        public Token(SearchSession parent, Type type, String text) {
            this.parent = parent;
            this.type = type;
            this.text = text;
            codePoints = this.text.codePoints().toArray();
            int lww = 0;
            for (int c : codePoints) if (!Character.isWhitespace(c)) {
                lww++;
                codes.put(c, codes.get(c) + 1);
            }
            lengthWithoutWhitespace = lww;
        }

        public boolean matches() {
            return parent.queryEmpty || (syncId == parent.syncId && score != -1);
        }

        public int match() {
            if (syncId == parent.syncId) return 0;
            syncId = parent.syncId;
            score = 0;
            if (parent.queryEmpty) return 0;
            Token query = parent.queries.get(type);
            int cost = 1;
            if (query == null) return cost;

            cost++;
            if (lengthWithoutWhitespace + 1 < query.lengthWithoutWhitespace) {
                score = -1;
                return cost;
            }

            cost++;
            int index = text.indexOf(query.text);
            if (index != -1) {
                score = index / 3;
                return cost;
            }

            int mismatches = 0;
            for (int c : query.codes.keySet()) {
                cost += 3;
                int count = codes.get(c);
                int qc = query.codes.get(c);
                if (count < qc) {
                    mismatches += qc - count;
                    if (mismatches > 1) {
                        score = -1;
                        return cost;
                    }
                }
            }

            score = 0;
            index = 0;
            boolean atSeparator = false;
            outer:
            for (int c : query.codePoints) {
                cost++;
                if (Character.isWhitespace(c)) {
                    atSeparator = true;
                    continue;
                }
                if (index >= codePoints.length) {
                    score += 100;
                    continue;
                }
                for (int i = index; i < codePoints.length; i++) {
                    cost++;
                    if (codePoints[i] != c) continue;
                    if (!atSeparator && i != index) score += 10;
                    else atSeparator = false;
                    score += i - index;
                    index = i + 1;
                    continue outer;
                }
                cost++;
                score += 100;
            }

            return cost;
        }

        public static int compare(@Nullable Token a, @Nullable Token b) {
            if (a == null) return b == null ? 0 : 1;
            if (b == null) return -1;
            return a.compareTo(b);
        }

        @Override
        public int compareTo(@NotNull Token o) {
            if (parent != o.parent) return 0;
            if (!matches()) return o.matches() ? 1 : 0;
            if (!o.matches()) return -1;
            if (type != o.type) return 0; // shouldn't happen
            return Integer.compare(score, o.score);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Token token = (Token) o;
            return Objects.equals(parent, token.parent) && type == token.type && Objects.equals(text, token.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parent, type, text);
        }

    }

    public enum Type {
        GENERIC,
        TOOLTIP,    // #
        TAG,        // $
        IDENTIFIER, // *
        MODID;      // @

        public static final List<Type> priority = List.of(MODID, IDENTIFIER, TAG, GENERIC, TOOLTIP);
    }

}
