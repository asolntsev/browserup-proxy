package com.browserup.harreader.filter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HarLog;

public final class HarLogFilter {

    private HarLogFilter() {
        // do nothing
    }

    /**
     * Search the entire log for the most recent entry.
     *
     * @return <code>HarEntry</code> for the most recently requested URL.
     */
    public static Optional<HarEntry> findMostRecentEntry(HarLog log) {
        return log.getEntries().stream().max(Comparator.comparing(HarEntry::getStartedDateTime));
    }

    /**
     * Search the entire log for the most recent entry whose request URL matches the given <code>urlPattern</code>.
     *
     * @param urlPattern Regular expression match of URL to find.
     *                   URLs are formatted as: scheme://host:port/path?querystring.
     *                   Port is not included in the URL if it is the standard port for the scheme.
     *                   Fragments (example.com/#fragment) should not be included in the URL.
     *                   If more than one URL found, return the most recently requested URL.
     *                   Pattern examples:
     *                   - Match a URL with "http" or "https" protocol, "example.com" domain, and "/index.html" exact
     *                   file path, with no query parameters:
     *                   "^(http|https)://example\\.com/index\\.html$"
     *                   - Match a URL with "http" protocol, "example.com" domain, "/customer" exact path, followed
     *                   by any query string:
     *                   "^http://example\\.com/customer\\?.*"
     *                   - Match a URL with "http" protocol, "example.com" domain, "/products" path, and exactly 1
     *                   UUID query parameter named "id":
     *                   "^http://example\\.com/products\\?id=[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0
     *                   -9a-fA-F]{4}\\-[0-9a-fA-F]{12}$"
     * @return <code>HarEntry</code> for the most recently requested URL matching the given <code>urlPattern</code>
     * pattern.
     */
    public static Optional<HarEntry> findMostRecentEntry(HarLog log, Pattern urlPattern) {
        return findEntries(log, urlPattern).stream().max(Comparator.comparing(HarEntry::getStartedDateTime));
    }

    /**
     * Search the entire log for entries whose request URL matches the given <code>urlPattern</code>.
     *
     * @param urlPattern Regular expression match of URL to find.
     *                   URLs are formatted as: scheme://host:port/path?querystring.
     *                   Port is not included in the URL if it is the standard port for the scheme.
     *                   Fragments (example.com/#fragment) should not be included in the URL.
     *                   If more than one URL found, use the most recently requested URL.
     *                   Pattern examples:
     *                   - Match a URL with "http" or "https" protocol, "example.com" domain, and "/index.html" exact
     *                   file path, with no query parameters:
     *                   "^(http|https)://example\\.com/index\\.html$"
     *                   - Match a URL with "http" protocol, "example.com" domain, "/customer" exact path, followed
     *                   by any query string:
     *                   "^http://example\\.com/customer\\?.*"
     *                   - Match a URL with "http" protocol, "example.com" domain, "/products" path, and exactly 1
     *                   UUID query parameter named "id":
     *                   "^http://example\\.com/products\\?id=[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0
     *                   -9a-fA-F]{4}\\-[0-9a-fA-F]{12}$"
     * @return A list of <code>HarEntry</code> for any requests whose URL matches the given <code>urlPattern</code>
     * pattern, or an empty list if none match.
     */
    public static List<HarEntry> findEntries(HarLog log, Pattern urlPattern) {
        return log.getEntries().stream()
                .filter(e -> urlPattern.matcher(e.getRequest().getUrl()).matches())
                .collect(Collectors.toList());
    }
}
