package com.example.template.api;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.template.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Sliding JWT sessions: whenever a valid token is past half its 10-minute life,
 * successful responses carry a fresh one ({@code X-Renewed-Token}) for the
 * client to persist. Active users slide forward; idle ones hit the hard expiry
 * and get bounced to login by the frontend.
 *
 * <p>The header is applied the moment the response status is known and only for
 * 2xx, so error responses never extend a session. Deciding it there (rather
 * than after the chain has run) means a large body flushing the buffer can't
 * drop the header.
 */
@Component
public class SessionRenewalFilter extends OncePerRequestFilter {

    static final String RENEWED_TOKEN_HEADER = "X-Renewed-Token";

    private final JwtService jwt;

    public SessionRenewalFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String renewed = jwt.renewIfDue(BearerToken.from(request));
        if (renewed == null) {
            chain.doFilter(request, response);
            return;
        }
        RenewingResponse wrapper = new RenewingResponse(response, renewed);
        chain.doFilter(request, wrapper);
        wrapper.applyIfDue();
    }

    private static final class RenewingResponse extends HttpServletResponseWrapper {

        private final String renewed;
        private boolean decided;

        private RenewingResponse(HttpServletResponse response, String renewed) {
            super(response);
            this.renewed = renewed;
        }

        @Override
        public void setStatus(int status) {
            super.setStatus(status);
            apply(status);
        }

        /** Handlers that never set a status still get their renewed token. */
        private void applyIfDue() {
            if (!decided) {
                apply(super.getStatus());
            }
        }

        private void apply(int status) {
            if (decided) {
                return;
            }
            decided = true;
            if (status >= HttpServletResponse.SC_OK && status < HttpServletResponse.SC_MULTIPLE_CHOICES) {
                super.setHeader(RENEWED_TOKEN_HEADER, renewed);
            }
        }
    }
}
