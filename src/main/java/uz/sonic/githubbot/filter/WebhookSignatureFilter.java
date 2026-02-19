package uz.sonic.githubbot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.sonic.githubbot.service.GitHubWebhookService;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class WebhookSignatureFilter extends OncePerRequestFilter {

    private final GitHubWebhookService webhookService;

    public WebhookSignatureFilter(GitHubWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/api/github/webhook".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String signature = request.getHeader("X-Hub-Signature-256");

        if (!webhookService.isValidSignature(body, signature)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid signature");
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() { return byteStream.available() == 0; }

                @Override
                public boolean isReady() { return true; }

                @Override
                public void setReadListener(ReadListener readListener) { }

                @Override
                public int read() { return byteStream.read(); }
            };
        }
    }
}
