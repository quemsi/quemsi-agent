package com.quemsi.agent.control;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import com.quemsi.agent.AgentCoordinator;
import com.quemsi.agent.AgentRuntimeDetector;
import com.quemsi.agent.flow.TimerImpl;
import com.quemsi.agent.service.FlowManager;
import com.quemsi.agent.service.SpringBeanManager;
import com.quemsi.model.flow.db.DataSourceFactory;
import com.quemsi.model.flow.out.Storage;

@RestController
public class ControlHomeController implements ErrorController {

    @Autowired
    private AgentCoordinator agentCoordinator;
    @Autowired
    private SpringBeanManager beanManager;
    @Autowired
    private FlowManager flowManager;
    @Autowired
    private ApplicationContext applicationContext;

    @Value("${spring.application.version:unknown}")
    private String agentVersion;
    @Value("${quemsi-api.client-id:}")
    private String clientId;
    @Value("${quemsi-api.server-url:}")
    private String apiUrl;
    @Value("${quemsi-ui.url:}")
    private String uiUrl;

    @GetMapping({ "/", "/index.html" })
    public ResponseEntity<String> home() throws IOException {
        return page(HttpStatus.OK);
    }

    @RequestMapping("/error")
    public ResponseEntity<String> error(HttpServletRequest request) throws IOException {
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus httpStatus = status != null ? HttpStatus.resolve(status) : HttpStatus.NOT_FOUND;
        if (httpStatus == null) {
            httpStatus = HttpStatus.NOT_FOUND;
        }
        return page(httpStatus);
    }

    private ResponseEntity<String> page(HttpStatus status) throws IOException {
        boolean ready = agentCoordinator.isInitialized();
        String html = loadTemplate("control-home/index.html");
        String resolvedUiUrl = resolveUiUrl(uiUrl, apiUrl);
        html = html.replace("{{VERSION}}", escapeHtml(blankToDash(agentVersion)))
                .replace("{{RUNTIME}}", escapeHtml(AgentRuntimeDetector.detect()))
                .replace("{{CLIENT_ID}}", escapeHtml(blankToDash(clientId)))
                .replace("{{API_URL}}", escapeHtml(blankToDash(apiUrl)))
                .replace("{{UI_URL}}", escapeHtml(resolvedUiUrl))
                .replace("{{UI_HOST}}", escapeHtml(displayHost(resolvedUiUrl)))
                .replace("{{STATUS}}", ready ? "Ready" : "Connecting")
                .replace("{{STATUS_CLASS}}", ready ? "ready" : "connecting")
                .replace("{{DATASOURCES}}", escapeHtml(joinNames(datasourceNames())))
                .replace("{{STORAGES}}", escapeHtml(joinNames(storageNames())))
                .replace("{{TIMERS}}", escapeHtml(joinNames(timerNames())))
                .replace("{{FLOWS}}", escapeHtml(joinNames(flowManager.flowNames())));
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private List<String> datasourceNames() {
        return applicationContext.getBeansOfType(DataSourceFactory.class).values().stream()
                .map(DataSourceFactory::getName)
                .toList();
    }

    private List<String> storageNames() {
        return beanManager.findStorages().stream()
                .map(Storage::getName)
                .toList();
    }

    private List<String> timerNames() {
        return applicationContext.getBeansOfType(TimerImpl.class).values().stream()
                .map(TimerImpl::getName)
                .toList();
    }

    private static String joinNames(Collection<String> names) {
        List<String> clean = names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
        return clean.isEmpty() ? "None yet" : String.join(", ", clean);
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    static String resolveUiUrl(String configured, String apiUrl) {
        String fromConfig = normalizeUiUrl(configured);
        if (fromConfig != null) {
            return fromConfig;
        }
        String fromApi = deriveUiUrl(apiUrl);
        return fromApi != null ? fromApi : "https://quemsi.com/app/";
    }

    private static String deriveUiUrl(String apiUrl) {
        URI api = parseHttpUri(apiUrl);
        if (api == null || api.getHost() == null || api.getHost().isBlank()) {
            return null;
        }
        String host = api.getHost();
        if (!isBrowserHost(host)) {
            return null;
        }
        int port = api.getPort();
        boolean dropPort = port < 0 || port == 8081 || port == 9081 || isDefaultPort(api.getScheme(), port);
        String authority = dropPort ? host : host + ":" + port;
        return api.getScheme() + "://" + authority + "/app/";
    }

    private static String normalizeUiUrl(String url) {
        URI uri = parseHttpUri(url);
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            return origin + "/app/";
        }
        return uri.toString();
    }

    private static URI parseHttpUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isBrowserHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(lower)
                || lower.endsWith(".localhost")
                || host.indexOf('.') >= 0
                || host.indexOf(':') >= 0;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static String displayHost(String url) {
        URI uri = parseHttpUri(url);
        if (uri == null || uri.getHost() == null) {
            return url;
        }
        return uri.getHost();
    }

    private static String loadTemplate(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
