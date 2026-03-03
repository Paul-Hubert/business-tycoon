package api;

import database.DB;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

/**
 * Validates Bearer tokens for all /api/v1/* endpoints.
 * Public paths (signup, login, config) bypass the check.
 * On success, attaches playerId as a request attribute.
 */
@WebFilter(urlPatterns = {"/api/v1/*"})
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = stripContextPath(req);

        if (isPublicPath(path, req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractBearerToken(req);
        if (token == null) {
            sendUnauthorized(resp, "missing_token");
            return;
        }

        Integer playerId = validateToken(token);
        if (playerId == null) {
            sendUnauthorized(resp, "invalid_token");
            return;
        }

        req.setAttribute("playerId", playerId);
        chain.doFilter(request, response);
    }

    private String stripContextPath(HttpServletRequest req) {
        String ctx = req.getContextPath();  // "" for ROOT webapp
        String uri = req.getRequestURI();
        return (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx))
               ? uri.substring(ctx.length())
               : uri;
    }

    private boolean isPublicPath(String path, String method) {
        return path.equals("/api/v1/auth/signup")
            || path.equals("/api/v1/auth/login")
            || path.equals("/api/v1/config");
    }

    private String extractBearerToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    /**
     * Validates the token in two checks:
     * 1. Check auth_tokens (temporary session tokens)
     * 2. Check api_keys (permanent API keys)
     * Returns the player_id on success, null on failure/expiry.
     */
    private Integer validateToken(String token) {
        try (Connection conn = DB.connect()) {
            // Check regular auth token
            String tokenSql = "SELECT player_id FROM auth_tokens WHERE token = ? AND expires_at > NOW()";
            try (PreparedStatement ps = conn.prepareStatement(tokenSql)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("player_id");
                }
            }

            // Check API key
            String apiKeySql = "SELECT player_id FROM api_keys WHERE api_key = ?";
            try (PreparedStatement ps = conn.prepareStatement(apiKeySql)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int playerId = rs.getInt("player_id");
                        // Update last_used timestamp
                        String updateSql = "UPDATE api_keys SET last_used = NOW() WHERE api_key = ?";
                        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                            updatePs.setString(1, token);
                            updatePs.executeUpdate();
                        }
                        return playerId;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] DB error validating token: " + e.getMessage());
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse resp, String errorCode) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json; charset=utf-8");
        resp.getWriter().write("{\"success\":false,\"data\":null,\"error\":\"" + errorCode + "\"}");
    }

    @Override public void init(FilterConfig config) {}
    @Override public void destroy() {}
}
