package com.platform.content.drawing;

import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Owner-approved 2026-11-10: the Drawing / Handwriting question type's two documents, checked and normalized by the
 * server (V91). Coordinates are fractions of the drawing area (0-1), so a drawing reads the same on any screen.
 *
 * <ul>
 *   <li>The author's guide ({@link #normalizeGuide}): {@code mode} FREE (draw freely), TRACE (go over a dotted
 *       reference) or GUIDED (write it yourself, with the example, start points and arrows beside you);
 *       {@code reference}/{@code referenceKm}: the letter, number or word to copy (English / Khmer);
 *       {@code template}: strokes the author drew, shown dotted to trace; {@code starts}: [x, y] start points;
 *       {@code arrows}: [x1, y1, x2, y2] directions; {@code baseline}, {@code grid}: writing guides.</li>
 *   <li>The child's answer ({@link #normalizeAnswer}): {@code {"strokes": [[[x, y], ...], ...]}} - refused when blank.</li>
 * </ul>
 */
public final class Drawings {

    public static final Set<String> MODES = Set.of("FREE", "TRACE", "GUIDED");
    static final int MAX_REFERENCE = 40;
    static final int MAX_TEMPLATE_STROKES = 60;
    static final int MAX_TEMPLATE_POINTS = 3000;
    static final int MAX_MARKS = 12;
    static final int MAX_ANSWER_STROKES = 300;
    static final int MAX_ANSWER_POINTS = 10_000;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Drawings() {
    }

    /** The guide as stored, or IllegalArgumentException (400) saying what is wrong. */
    public static String normalizeGuide(JsonNode guide) {
        if (guide == null || !guide.isObject()) {
            throw new IllegalArgumentException("A drawing question needs its guide.");
        }
        String mode = text(guide, "mode", 10);
        if (mode == null || !MODES.contains(mode)) {
            throw new IllegalArgumentException("Choose how the child draws: free, tracing or guided.");
        }
        String reference = text(guide, "reference", MAX_REFERENCE);
        String referenceKm = text(guide, "referenceKm", MAX_REFERENCE);
        ArrayNode template = strokes(guide.get("template"), MAX_TEMPLATE_STROKES, MAX_TEMPLATE_POINTS, "tracing template");
        if (!"FREE".equals(mode) && reference == null && referenceKm == null && (template == null || template.isEmpty())) {
            throw new IllegalArgumentException("Tracing and guided writing need something to copy: a letter, number or word, or a drawn template.");
        }
        ObjectNode out = NODES.objectNode();
        out.put("mode", mode);
        if (reference != null) out.put("reference", reference);
        if (referenceKm != null) out.put("referenceKm", referenceKm);
        if (template != null && !template.isEmpty()) out.set("template", template);
        ArrayNode starts = marks(guide.get("starts"), 2, "start point");
        if (!starts.isEmpty()) out.set("starts", starts);
        ArrayNode arrows = marks(guide.get("arrows"), 4, "arrow");
        if (!arrows.isEmpty()) out.set("arrows", arrows);
        out.put("baseline", guide.path("baseline").asBoolean(false));
        out.put("grid", guide.path("grid").asBoolean(false));
        return JSON.writeValueAsString(out);
    }

    /**
     * The child's drawing as stored, or {@link IllegalArgumentException} with the reason (the caller turns it into
     * its own refusal). A drawing with no line in it is blank and refused: it doesn't count.
     */
    public static String normalizeAnswer(String submitted) {
        JsonNode parsed;
        try {
            parsed = JSON.readTree(submitted == null ? "" : submitted);
        } catch (RuntimeException notJson) {
            throw new IllegalArgumentException("A drawing is sent as its strokes.");
        }
        if (parsed == null || !parsed.isObject()) {
            throw new IllegalArgumentException("A drawing is sent as its strokes.");
        }
        ArrayNode strokes = strokes(parsed.get("strokes"), MAX_ANSWER_STROKES, MAX_ANSWER_POINTS, "drawing");
        boolean drawn = false;
        if (strokes != null) {
            for (JsonNode stroke : strokes) {
                if (stroke.size() >= 2) {
                    drawn = true;
                    break;
                }
            }
        }
        if (!drawn) {
            throw new IllegalArgumentException("The drawing is empty.");
        }
        ObjectNode out = NODES.objectNode();
        out.set("strokes", strokes);
        return JSON.writeValueAsString(out);
    }

    private static String text(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw new IllegalArgumentException("The guide's " + field + " must be text.");
        String text = value.asString().strip();
        if (text.isEmpty()) return null;
        if (text.codePointCount(0, text.length()) > max) {
            throw new IllegalArgumentException("Keep the text to copy to " + max + " characters.");
        }
        return text;
    }

    /** Strokes: arrays of [x, y] points in 0-1, within the limits; null when absent. */
    private static ArrayNode strokes(JsonNode node, int maxStrokes, int maxPoints, String what) {
        if (node == null || node.isNull()) return null;
        if (!node.isArray() || node.size() > maxStrokes) {
            throw new IllegalArgumentException("The " + what + " has too many lines or isn't a list of lines.");
        }
        ArrayNode out = NODES.arrayNode();
        int points = 0;
        for (JsonNode stroke : node) {
            if (!stroke.isArray()) throw new IllegalArgumentException("Each line of the " + what + " is a list of points.");
            ArrayNode line = NODES.arrayNode();
            for (JsonNode point : stroke) {
                if (++points > maxPoints) throw new IllegalArgumentException("The " + what + " is too detailed.");
                line.add(point(point, what));
            }
            if (!line.isEmpty()) out.add(line);
        }
        return out;
    }

    private static ArrayNode marks(JsonNode node, int numbers, String what) {
        ArrayNode out = NODES.arrayNode();
        if (node == null || node.isNull()) return out;
        if (!node.isArray() || node.size() > MAX_MARKS) {
            throw new IllegalArgumentException("Use up to " + MAX_MARKS + " of each " + what + ".");
        }
        for (JsonNode mark : node) {
            if (!mark.isArray() || mark.size() != numbers) throw new IllegalArgumentException("A " + what + " isn't where it should be.");
            ArrayNode values = NODES.arrayNode();
            for (JsonNode n : mark) values.add(coordinate(n, what));
            out.add(values);
        }
        return out;
    }

    private static ArrayNode point(JsonNode point, String what) {
        if (!point.isArray() || point.size() != 2) throw new IllegalArgumentException("A point of the " + what + " isn't [x, y].");
        ArrayNode out = NODES.arrayNode();
        out.add(coordinate(point.get(0), what));
        out.add(coordinate(point.get(1), what));
        return out;
    }

    /** A fraction of the area, to four places. */
    private static double coordinate(JsonNode n, String what) {
        if (n == null || !n.isNumber()) throw new IllegalArgumentException("The " + what + " has a point that isn't a number.");
        double v = n.asDouble();
        if (!(v >= 0 && v <= 1)) throw new IllegalArgumentException("The " + what + " goes outside its area.");
        return Math.round(v * 10_000) / 10_000.0;
    }
}
