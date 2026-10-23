package com.platform.security;

import com.platform.common.web.CorrelationIdFilter;
import com.platform.common.web.ServerText;
import com.platform.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M10a: what a child's device may do. Its sign-in is the parent's account (whose family gives access) narrowed to one
 * child, so everything outside that child's learning space is refused here - before any controller - however the
 * family guards would have answered: parent pages, billing, settings, other children, schools, studio, everything.
 *
 * <p>Allowed, for the scoped child only: reading the child and what the child space shows (paths, skills, today's
 * time, the break setting), placing due reviews, choosing the buddy and the space's language, reading the journey and next step of the child's
 * own enrolments, reading published content for the child, and starting, reading, answering, completing or leaving
 * the child's own activity attempts; plus refreshing and ending its own sign-in. An id in the path that isn't the
 * child's (an enrolment or attempt of a sibling) is refused like any other path. Fail closed: no match, no access.
 */
public class ChildDeviceScopeFilter extends OncePerRequestFilter {

    static final String REFUSED = "This device can only open one child's learning space";

    private static final String UUID_RE = "([0-9a-fA-F-]{36})";

    /** A request a child's device may make, and what in it must be the child's. */
    private record Rule(String method, Pattern path, Owner owner) {
    }

    private enum Owner { CHILD, ENROLLMENT, ATTEMPT, CONTENT, NONE }

    private static final List<Rule> RULES = List.of(
            rule("GET", "/api/v1/children/" + UUID_RE, Owner.CHILD),
            rule("GET", "/api/v1/children/" + UUID_RE + "/(?:enrollments|skills|today|break-reminder)", Owner.CHILD),
            rule("POST", "/api/v1/children/" + UUID_RE + "/(?:reviews/place-due|activity-attempts)", Owner.CHILD),
            rule("PUT", "/api/v1/children/" + UUID_RE + "/(?:buddy|language)", Owner.CHILD),
            rule("GET", "/api/v1/enrollments/" + UUID_RE + "/(?:journey|next-activity)", Owner.ENROLLMENT),
            rule("GET", "/api/v1/activity-versions/" + UUID_RE, Owner.CONTENT),
            rule("GET", "/api/v1/activity-attempts/" + UUID_RE + "(?:/answers)?", Owner.ATTEMPT),
            rule("POST", "/api/v1/activity-attempts/" + UUID_RE + "/(?:answers|complete|abandon)", Owner.ATTEMPT),
            rule("GET", "/api/v1/media/[^/]+", Owner.NONE),
            rule("POST", "/api/v1/auth/(?:refresh|logout)", Owner.NONE));

    private static Rule rule(String method, String path, Owner owner) {
        return new Rule(method, Pattern.compile(path), owner);
    }

    private final ObjectProvider<JdbcTemplate> jdbc;

    public ChildDeviceScopeFilter(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Object scope = request.getAttribute(JwtAuthenticationFilter.CHILD_SCOPE_ATTRIBUTE);
        if (scope instanceof UUID childId && !allowed(request, childId)) {
            refuse(request, response);
            return;
        }
        chain.doFilter(request, response);

    }

    boolean allowed(HttpServletRequest request, UUID childId) {

        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Rule rule : RULES) {
            if (!rule.method().equalsIgnoreCase(request.getMethod())) {
                continue;
            }
            Matcher matcher = rule.path().matcher(path);
            if (!matcher.matches()) {
                continue;
            }
            return switch (rule.owner()) {
                case NONE -> true;
                case CHILD -> childId.toString().equalsIgnoreCase(matcher.group(1));
                // The content read is for a child named in the query (TD-13): it must be this one.
                case CONTENT -> childId.toString().equalsIgnoreCase(request.getParameter("childId"));
                case ENROLLMENT -> belongs("select child_id from enrollments where id = ?", matcher.group(1), childId);
                case ATTEMPT -> belongs("select child_id from activity_attempts where id = ?", matcher.group(1), childId);
            };
        }
        return false;

    }

    private boolean belongs(String sql, String id, UUID childId) {

        UUID parsed;
        try {
            parsed = UUID.fromString(id);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        List<UUID> owners = jdbc.getObject().queryForList(sql, UUID.class, parsed);
        return owners.size() == 1 && childId.equals(owners.getFirst());

    }

    private static void refuse(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Fixed text, a timestamp and a constrained correlation id (as ProblemDetailAuthenticationEntryPoint).
        String detail = ServerText.forRequest(request, REFUSED, 403);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        String body = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"" + detail + "\","
                + "\"childDeviceScope\":true,\"timestamp\":\"" + Instant.now() + "\","
                + "\"correlationId\":" + (correlationId == null ? "null" : "\"" + correlationId + "\"") + "}";
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);

    }

}
