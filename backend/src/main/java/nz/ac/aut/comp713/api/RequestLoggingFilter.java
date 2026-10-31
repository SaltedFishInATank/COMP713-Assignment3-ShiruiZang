package nz.ac.aut.comp713.api;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;

/** Same-origin browser deployment and concise request evidence in Payara's server.log. */
@WebFilter("/*")
public class RequestLoggingFilter implements Filter {
    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        HttpServletResponse result = (HttpServletResponse) response;
        result.setHeader("X-Content-Type-Options", "nosniff");
        result.setHeader("X-Frame-Options", "DENY");
        result.setHeader("Referrer-Policy", "same-origin");
        result.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; "
                + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        if (http.getRequestURI().startsWith(http.getContextPath() + "/api/")) {
            result.setHeader("Cache-Control", "no-store");
        }
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            double milliseconds = (System.nanoTime() - started) / 1_000_000.0;
            LOG.info(() -> String.format(Locale.ROOT, "request method=%s path=%s status=%d duration_ms=%.2f",
                    http.getMethod(), http.getRequestURI(), result.getStatus(), milliseconds));
        }
    }
}
