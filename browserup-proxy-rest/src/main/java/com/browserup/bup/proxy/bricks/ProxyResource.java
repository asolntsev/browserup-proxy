package com.browserup.bup.proxy.bricks;

import com.browserup.bup.MitmProxyServer;
import com.browserup.bup.exception.ProxyExistsException;
import com.browserup.bup.exception.ProxyPortsExhaustedException;
import com.browserup.bup.exception.UnsupportedCharsetException;
import com.browserup.bup.filters.JavascriptRequestResponseFilter;
import com.browserup.bup.mitmproxy.MitmProxyProcessManager.MitmProxyLoggingLevel;
import com.browserup.bup.proxy.CaptureType;
import com.browserup.bup.proxy.MitmProxyManager;
import com.browserup.bup.proxy.auth.AuthType;
import com.browserup.bup.util.BrowserUpHttpUtil;
import com.browserup.harreader.model.Har;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.sitebricks.At;
import com.google.sitebricks.client.transport.Json;
import com.google.sitebricks.client.transport.Text;
import com.google.sitebricks.headless.Reply;
import com.google.sitebricks.headless.Request;
import com.google.sitebricks.headless.Service;
import com.google.sitebricks.http.Delete;
import com.google.sitebricks.http.Get;
import com.google.sitebricks.http.Post;
import com.google.sitebricks.http.Put;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

@At("/proxy")
@Service
public class ProxyResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProxyResource.class);

    private final MitmProxyManager proxyManager;

    @Inject
    public ProxyResource(MitmProxyManager proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Get
    public Reply<?> getProxies() {
        LOG.info("GET /");
        Collection<ProxyDescriptor> proxyList = proxyManager.get().stream()
                .map(proxy -> new ProxyDescriptor(proxy.getPort()))
                .collect(toList());
        return Reply.with(new ProxyListDescriptor(proxyList)).as(Json.class);
    }

    @Post
    public Reply<?> newProxy(Request request) {
        LOG.info("POST /");
        LOG.info(request.params().toString());
        String systemProxyHost = System.getProperty("http.proxyHost");
        String systemProxyPort = System.getProperty("http.proxyPort");
        String systemNonProxyHosts = System.getProperty("http.nonProxyHosts");

        String httpProxy = request.param("httpProxy");
        String httpNonProxyHosts = request.param("httpNonProxyHosts");

        String proxyUsername = request.param("proxyUsername");
        String proxyPassword = request.param("proxyPassword");
        boolean upstreamProxyHttps = "true".equals(request.param("proxyHTTPS"));

        Hashtable<String, String> options = new Hashtable<>();

        // If the upstream proxy is specified via query params that should override any default system level proxy.
        String upstreamHttpProxy = null;
        if (httpProxy != null) {
            upstreamHttpProxy = httpProxy;
        } else if ((systemProxyHost != null) && (systemProxyPort != null)) {
            upstreamHttpProxy = String.format("%s:%s", systemProxyHost, systemProxyPort);
        }

        // If the upstream proxy is defined, we should add the non proxy hosts (proxy exceptions) as well.
        List<String> upstreamNonProxyHosts = null;
        if (upstreamHttpProxy != null) {

            // override system non proxy hosts with the provided ones
            if (httpNonProxyHosts != null) {
                upstreamNonProxyHosts = Arrays.asList(httpNonProxyHosts.split("\\|"));
            } else if (systemNonProxyHosts != null) {
                upstreamNonProxyHosts = Arrays.asList(systemNonProxyHosts.split("\\|"));
            }
        }

        String paramBindAddr = request.param("bindAddress");
        String paramServerBindAddr = request.param("serverBindAddress");
        Integer paramPort = request.param("port") == null ? null : Integer.parseInt(request.param("port"));

        String useEccString = request.param("useEcc");
        boolean useEcc = Boolean.parseBoolean(useEccString);

        String loggingLevel = request.param("mitmProxyLoggingLevel");
        MitmProxyLoggingLevel level = MitmProxyLoggingLevel.info;
        if (StringUtils.isNotEmpty(loggingLevel)) {
            level = MitmProxyLoggingLevel.valueOf(loggingLevel);
        }

        String trustAllServersString = request.param("trustAllServers");
        boolean trustAllServers = Boolean.parseBoolean(trustAllServersString);

        LOG.debug("POST proxy instance on bindAddress `{}` & port `{}` & serverBindAddress `{}`",
                paramBindAddr, paramPort, paramServerBindAddr);
        MitmProxyServer proxy;
        try {
            proxy = proxyManager.create(upstreamHttpProxy, upstreamProxyHttps, upstreamNonProxyHosts, proxyUsername, proxyPassword, paramPort, paramBindAddr, paramServerBindAddr, useEcc, trustAllServers, level);
        } catch (ProxyExistsException ex) {
            return Reply.with(new ProxyDescriptor(ex.getPort())).status(455).as(Json.class);
        } catch (ProxyPortsExhaustedException ex) {
            return Reply.saying().status(456);
        } catch (Exception ex) {
            StringWriter s = new StringWriter();
            ex.printStackTrace(new PrintWriter(s));
            return Reply.with(s).as(Text.class).status(550);
        }
        return Reply.with(new ProxyDescriptor(proxy.getPort())).as(Json.class);
    }

    @Get
    @At("/:port/har")
    public Reply<?> getHar(@Named("port") int port, Request request) {
        LOG.info("GET /{}/har", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        boolean cleanHar = "true".equals(request.param("cleanHar"));
        Har har = proxy.getHar(cleanHar);

        return Reply.with(har).as(Json.class);
    }

    @Put
    @At("/:port/har")
    public Reply<?> newHar(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/har", port);
        LOG.info(request.params().toString());
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String initialPageRef = request.param("initialPageRef");
        String initialPageTitle = request.param("initialPageTitle");
        Har oldHar = proxy.newHar(initialPageRef, initialPageTitle);

        String captureHeaders = request.param("captureHeaders");
        String captureContent = request.param("captureContent");
        String captureBinaryContent = request.param("captureBinaryContent");
        Set<CaptureType> captureTypes = new HashSet<>();
        if (Boolean.parseBoolean(captureHeaders)) {
            captureTypes.addAll(CaptureType.getHeaderCaptureTypes());
        }
        if (Boolean.parseBoolean(captureContent)) {
            captureTypes.addAll(CaptureType.getAllContentCaptureTypes());
        }
        if (Boolean.parseBoolean(captureBinaryContent)) {
            captureTypes.addAll(CaptureType.getBinaryContentCaptureTypes());
        }
        proxy.setHarCaptureTypes(captureTypes);

        String captureCookies = request.param("captureCookies");
        if (Boolean.parseBoolean(captureCookies)) {
            proxy.enableHarCaptureTypes(CaptureType.getCookieCaptureTypes());
        }

        if (oldHar != null) {
            return Reply.with(oldHar).as(Json.class);
        } else {
            return Reply.saying().noContent();
        }
    }

    @Put
    @At("/:port/har/pageRef")
    public Reply<?> setPage(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/har/pageRef", port);
        LOG.info(request.params().toString());
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String pageRef = request.param("pageRef");
        String pageTitle = request.param("pageTitle");
        proxy.newPage(pageRef, pageTitle);

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/har/commands/endPage")
    public Reply<?> endPage(@Named("port") int port, Request request) {
        LOG.info("POST /{}/commands/endPage", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.endPage();

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/har/commands/endHar")
    public Reply<?> endHar(@Named("port") int port, Request request) {
        LOG.info("POST /{}/commands/endHar", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.endHar();

        return Reply.saying().ok();
    }

    @Get
    @At("/:port/blocklist")
    public Reply<?> getBlocklist(@Named("port") int port, Request request) {
        LOG.info("GET /{}/blocklist", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        return Reply.with(proxy.getBlocklist()).as(Json.class);
    }

    @Put
    @At("/:port/blocklist")
    public Reply<?> blocklist(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/blocklist", port);
        LOG.info(request.params().toString());
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String blocklist = request.param("regex");
        int responseCode = parseResponseCode(request.param("status"));
        String method = request.param("method");
        proxy.blocklistRequests(blocklist, responseCode, method);

        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/blocklist")
    public Reply<?> clearBlocklist(@Named("port") int port, Request request) {
        LOG.info("DELETE /{}/blocklist", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.clearBlocklist();
        return Reply.saying().ok();
    }

    @Get
    @At("/:port/allowlist")
    public Reply<?> getAllowlist(@Named("port") int port, Request request) {
        LOG.info("GET /{}/allowlist", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        return Reply.with(proxy.getAllowlistUrls()).as(Json.class);
    }

    @Put
    @At("/:port/allowlist")
    public Reply<?> allowlist(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/allowlist", port);
        LOG.info(request.params().toString());
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String regex = request.param("regex");
        int responseCode = parseResponseCode(request.param("status"));
        proxy.allowlistRequests(Arrays.asList(regex.split(",")), responseCode);

        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/allowlist")
    public Reply<?> clearAllowlist(@Named("port") int port, Request request) {
        LOG.info("DELETE /{}/allowlist", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.disableAllowlist();
        return Reply.saying().ok();
    }

    @Post
    @At("/:port/auth/basic/:domain")
    public Reply<?> autoBasicAuth(@Named("port") int port, @Named("domain") String domain, Request request) {
        LOG.info("POST /{}/auth/basic/{}", port, domain);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        Map<String, String> credentials = request.read(HashMap.class).as(Json.class);
        proxy.autoAuthorization(domain, credentials.get("username"), credentials.get("password"), AuthType.BASIC);

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/headers")
    public Reply<?> updateHeaders(@Named("port") int port, Request request) {
        LOG.info("POST /{}/headers", port);

        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        Map<String, String> headers = request.read(Map.class).as(Json.class);
        headers.forEach(proxy::addHeader);
        return Reply.saying().ok();
    }

    @Post
    @At("/:port/filter/request")
    public Reply<?> addRequestFilter(@Named("port") int port, Request request) throws IOException, ScriptException {
        LOG.info("POST /{}/filter/request", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        JavascriptRequestResponseFilter requestResponseFilter = new JavascriptRequestResponseFilter();

        String script = getEntityBodyFromRequest(request);
        requestResponseFilter.setRequestFilterScript(script);

        proxy.addRequestFilter(requestResponseFilter);

        return Reply.saying().ok();
    }

    @Post
    @At("/:port/filter/response")
    public Reply<?> addResponseFilter(@Named("port") int port, Request request) throws IOException, ScriptException {
        LOG.info("POST /{}/filter/response", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        JavascriptRequestResponseFilter requestResponseFilter = new JavascriptRequestResponseFilter();

        String script = getEntityBodyFromRequest(request);
        requestResponseFilter.setResponseFilterScript(script);

        proxy.addResponseFilter(requestResponseFilter);

        return Reply.saying().ok();
    }

    @Put
    @At("/:port/limit")
    public Reply<?> limit(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/limit", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String upstreamKbps = request.param("upstreamKbps");
        if (upstreamKbps != null) {
            try {
                long upstreamBytesPerSecond = Long.parseLong(upstreamKbps) * 1024;
                proxy.setWriteBandwidthLimit(upstreamBytesPerSecond);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid upstreamKbps value");
                return Reply.saying().badRequest();
            }
        }

        String upstreamBps = request.param("upstreamBps");
        if (upstreamBps != null) {
            try {
                proxy.setWriteBandwidthLimit(Integer.parseInt(upstreamBps));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid upstreamBps value");
                return Reply.saying().badRequest();
            }
        }

        String downstreamKbps = request.param("downstreamKbps");
        if (downstreamKbps != null) {
            try {
                long downstreamBytesPerSecond = Long.parseLong(downstreamKbps) * 1024;
                proxy.setReadBandwidthLimit(downstreamBytesPerSecond);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid downstreamKbps value");
                return Reply.saying().badRequest();
            }
        }

        String downstreamBps = request.param("downstreamBps");
        if (downstreamBps != null) {
            try {
                proxy.setReadBandwidthLimit(Integer.parseInt(downstreamBps));
            } catch (NumberFormatException e) {
                LOG.warn("Invalid downstreamBps value");
                return Reply.saying().badRequest();
            }
        }

        String latency = request.param("latency");
        if (latency != null) {
            try {
                proxy.setLatency(Long.parseLong(latency), TimeUnit.MILLISECONDS);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid latency value");
                return Reply.saying().badRequest();
            }
        }

        if (request.param("upstreamMaxKB") != null) {
            LOG.warn("upstreamMaxKB no longer supported");
            return Reply.saying().badRequest();
        }
        if (request.param("downstreamMaxKB") != null) {
            LOG.warn("downstreamMaxKB no longer supported");
            return Reply.saying().badRequest();
        }
        if (request.param("payloadPercentage") != null) {
            LOG.warn("payloadPercentage no longer supported");
            return Reply.saying().badRequest();
        }
        if (request.param("maxBitsPerSecond") != null) {
            LOG.warn("maxBitsPerSecond no longer supported");
            return Reply.saying().badRequest();
        }
        if (request.param("enable") != null) {
            LOG.warn("enable no longer supported. Limits, if set, will always be enabled.");
            return Reply.saying().badRequest();
        }

        return Reply.saying().ok();
    }

    @Put
    @At("/:port/timeout")
    public Reply<?> timeout(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/timeout", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String requestTimeout = request.param("requestTimeout");
        if (requestTimeout != null) {
            try {
                proxy.setRequestTimeout(Integer.parseInt(requestTimeout), TimeUnit.MILLISECONDS);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid requestTimeout value");
                return Reply.saying().badRequest();
            }
        }
        String readTimeout = request.param("readTimeout");
        if (readTimeout != null) {
            try {
                proxy.setIdleConnectionTimeout(Integer.parseInt(readTimeout), TimeUnit.MILLISECONDS);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid readTimeout value");
                return Reply.saying().badRequest();
            }
        }
        String connectionTimeout = request.param("connectionTimeout");
        if (connectionTimeout != null) {
            try {
                proxy.setConnectTimeout(Integer.parseInt(connectionTimeout), TimeUnit.MILLISECONDS);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid connectionTimeout value");
                return Reply.saying().badRequest();
            }
        }
        String dnsCacheTimeout = request.param("dnsCacheTimeout");
        if (dnsCacheTimeout != null) {
            try {
                proxy.getHostNameResolver().setPositiveDNSCacheTimeout(Integer.parseInt(dnsCacheTimeout), TimeUnit.SECONDS);
                proxy.getHostNameResolver().setNegativeDNSCacheTimeout(Integer.parseInt(dnsCacheTimeout), TimeUnit.SECONDS);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid dnsCacheTimeout value");
                return Reply.saying().badRequest();
            }
        }
        return Reply.saying().ok();
    }

    @Delete
    @At("/:port")
    public Reply<?> delete(@Named("port") int port) {
        LOG.info("DELETE /{}", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxyManager.delete(port);
        return Reply.saying().ok();
    }

    @Post
    @At("/:port/hosts")
    public Reply<?> remapHosts(@Named("port") int port, Request request) {
        LOG.info("POST /{}/hosts", port);
        LOG.info(request.params().toString());
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        @SuppressWarnings("unchecked") Map<String, String> headers = request.read(Map.class).as(Json.class);

        headers.forEach((key, value) -> {
            proxy.getHostNameResolver().remapHost(key, value);
            proxy.getHostNameResolver().setNegativeDNSCacheTimeout(0, TimeUnit.SECONDS);
            proxy.getHostNameResolver().setPositiveDNSCacheTimeout(0, TimeUnit.SECONDS);
            proxy.getHostNameResolver().clearDNSCache();
        });

        return Reply.saying().ok();
    }


    @Put
    @At("/:port/wait")
    public Reply<?> wait(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/wait", port);
        LOG.info(request.params().toString());
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String quietPeriodInMs = request.param("quietPeriodInMs");
        String timeoutInMs = request.param("timeoutInMs");
        proxy.waitForQuiescence(Long.parseLong(quietPeriodInMs), Long.parseLong(timeoutInMs), TimeUnit.MILLISECONDS);
        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/dns/cache")
    public Reply<?> clearDnsCache(@Named("port") int port) {
        LOG.info("DELETE /{}/dns/cache", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }
        proxy.getHostNameResolver().clearDNSCache();

        return Reply.saying().ok();
    }

    @Put
    @At("/:port/rewrite")
    public Reply<?> rewriteUrl(@Named("port") int port, Request request) {
        LOG.info("PUT /{}/rewrite", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        String match = request.param("matchRegex");
        String replace = request.param("replace");
        proxy.rewriteUrl(match, replace);
        return Reply.saying().ok();
    }

    @Delete
    @At("/:port/rewrite")
    public Reply<?> clearRewriteRules(@Named("port") int port, Request request) {
        LOG.info("DELETE /{}/rewrite", port);
        MitmProxyServer proxy = proxyManager.get(port);
        if (proxy == null) {
            return Reply.saying().notFound();
        }

        proxy.clearRewriteRules();
        return Reply.saying().ok();
    }

    @Put
    @At("/:port/retry")
    public Reply<?> retryCount(@Named("port") int port, Request request) {
        LOG.warn("/port/retry API is no longer supported");
        return Reply.saying().badRequest();
    }

    private int parseResponseCode(String response) {
        int responseCode = 200;
        if (response != null) {
            try {
                responseCode = Integer.parseInt(response);
            } catch (NumberFormatException e) {
            }
        }
        return responseCode;
    }

    public static class ProxyDescriptor {
        private int port;

        public ProxyDescriptor() {
        }

        public ProxyDescriptor(int port) {
            this.port = port;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class ProxyListDescriptor {
        private Collection<ProxyDescriptor> proxyList;

        public ProxyListDescriptor() {
        }

        public ProxyListDescriptor(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
        }

        public Collection<ProxyDescriptor> getProxyList() {
            return proxyList;
        }

        public void setProxyList(Collection<ProxyDescriptor> proxyList) {
            this.proxyList = proxyList;
        }
    }

    private String getEntityBodyFromRequest(Request request) throws IOException {
        String contentTypeHeader = request.header("Content-Type");
        Charset charset;
        try {
            charset = BrowserUpHttpUtil.readCharsetInContentTypeHeader(contentTypeHeader);
        } catch (UnsupportedCharsetException e) {
            java.nio.charset.UnsupportedCharsetException cause = e.getUnsupportedCharsetExceptionCause();
            LOG.error("Character set declared in Content-Type header is not supported. Content-Type header: {}", contentTypeHeader, cause);

            throw cause;
        }

        if (charset == null) {
            charset = BrowserUpHttpUtil.DEFAULT_HTTP_CHARSET;
        }

        ByteArrayOutputStream entityBodyBytes = new ByteArrayOutputStream();
        request.readTo(entityBodyBytes);

        return new String(entityBodyBytes.toByteArray(), charset);
    }



    private Optional<Long> getAssertionTimeFromRequest(Request request) {
        String timeParam = request.param("milliseconds");
        if (StringUtils.isEmpty(timeParam)) {
            LOG.warn("Time parameter not present");
            return Optional.empty();
        }

        Long time;
        try {
            time = Long.valueOf(timeParam);
        } catch (Exception ex) {
            LOG.warn("Time parameter not valid", ex);
            return Optional.empty();
        }
        return Optional.of(time);
    }
}
