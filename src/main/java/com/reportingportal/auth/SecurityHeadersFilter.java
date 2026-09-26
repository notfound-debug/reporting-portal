package com.reportingportal.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * First filter in the chain: adds browser security headers to every response.
 *
 * These are defence in depth. The main XSS defence is escaping every value in
 * the JSPs; the Content-Security-Policy means that even if an escape were
 * missed, an injected script would not run, because the page may only load
 * scripts from this server and the Chart.js CDN, and inline scripts are not allowed.
 */
public class SecurityHeadersFilter implements Filter {

    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://cdn.jsdelivr.net",
            "style-src 'self'",
            "img-src 'self' data:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        // Do not guess a content type (e.g. treat an uploaded text as HTML).
        http.setHeader("X-Content-Type-Options", "nosniff");
        // Older browsers' version of frame-ancestors 'none': no clickjacking in an iframe.
        http.setHeader("X-Frame-Options", "DENY");
        // Report URLs contain parameters; do not send them to other sites.
        http.setHeader("Referrer-Policy", "same-origin");
        chain.doFilter(request, response);
    }
}
