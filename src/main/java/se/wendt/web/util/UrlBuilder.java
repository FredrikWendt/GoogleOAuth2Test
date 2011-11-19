package se.wendt.web.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class UrlBuilder {

	private final String baseUrl;
	private final Map<String, String> parameters = new HashMap<String, String>();

	public UrlBuilder(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public UrlBuilder setQueryStringParameter(String parameterName, String parameterValue) {
		parameters.put(urlencode(parameterName), urlencode(parameterValue));
		return this;
	}
	
	private String urlencode(String parameterValue) {
		try {
			return URLEncoder.encode(parameterValue, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException("There's something fishy going on - your JVM doesn't support UTF-8?", e);
		}
	}

	/**
	 * This method is not thread-safe.
	 */
	@Override
	public String toString() {
		StringBuilder full = new StringBuilder(baseUrl);
		boolean first = true;
		for (Entry<String, String> pair : parameters.entrySet()) {
			if (first) {
				full.append("?");
				first = false;
			} else {
				full.append("&");
			}
			full.append(pair.getKey());
			full.append("=");
			full.append(pair.getValue());
		}
		return full.toString();
	}
	
}
