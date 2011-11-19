package se.wendt.web.oauth.google;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.sf.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

import se.wendt.web.util.UrlBuilder;

/**
 * Incomplete implementation (doesn't re-validate or refresh access tokens) -
 * essentially this filter is only intended to be use used once.
 */
public class GoogleAuthenticationFilter implements Filter {

	/** URL to where the OAuth end point is available at. */
	public static final String OAUTH2_END_POINT = "https://accounts.google.com/o/oauth2/auth";

	/** URL to end point where one can request access tokens. */
	public static final String ACCESS_TOKEN_REQUEST_END_POINT = "https://accounts.google.com/o/oauth2/token";

	/** URL to user info API. */
	public static final String USERINFO_API_END_POINT = "https://www.googleapis.com/oauth2/v1/userinfo";

	/** Used to tell this filter which state the user is in. */
	private static final String STATE = "try";
	
	/** Stores client_id and alike. */
	private Properties properties;

	@Override
	public void destroy() {
	}

	@Override
	public void doFilter(ServletRequest arg0, ServletResponse arg1, FilterChain arg2) throws IOException,
			ServletException {
		HttpServletRequest request = (HttpServletRequest) arg0;
		HttpServletResponse response = (HttpServletResponse) arg1;
		HttpSession session = request.getSession();

		if (requestIsLoginAttempt(request)) {
			redirectToGoogle_accessTokenRequest(response);
			return;
		}

		if (requestIsLogoutAttempt(request)) {
			session.invalidate();
			redirectToMainPage(response);
			return;
		}

		if (sessionIsAuthenticated(session)) {
			redirectToMainPage(response);
			return;
		}

		if (requestContainsAuthenticationCredentials(request)) {
			processAuthenticationCredentials(request);
		}

		redirectToMainPage(response);
	}

	private void processAuthenticationCredentials(HttpServletRequest request) {
		String authorizationCode = extractAuthorizationCode(request);
		String accessToken = exchangeAuthorizationCodeForAccessToken(authorizationCode);
		Map<String, String> userInfo = getUserInfoFromGoogleUserInfoApi(accessToken);
		request.getSession().setAttribute("user", userInfo);
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getUserInfoFromGoogleUserInfoApi(String accessToken) {
		UrlBuilder url = new UrlBuilder(USERINFO_API_END_POINT);
		url.setQueryStringParameter("access_token", accessToken);
		String result = doit(url.toString());
		JSONObject json = JSONObject.fromObject(result);
		Map<String, String> userInfo = new HashMap<String, String>();
		for (Iterator<String> keys = json.keys(); keys.hasNext();) {
			String key = keys.next();
			userInfo.put(key, json.getString(key));
		}
		return userInfo;
	}

	private String verifyToken(String accessToken) {
		String baseUrl = "https://www.googleapis.com/oauth2/v1/tokeninfo";
		UrlBuilder url = new UrlBuilder(baseUrl);
		url.setQueryStringParameter("access_token", accessToken);
		String result = doit(url.toString());
		return result;
	}

	protected String doit(String urlSource) {
		try {
			URL url = new URL(urlSource);
			URLConnection con = url.openConnection();
			Pattern p = Pattern.compile("text/html;\\s+charset=([^\\s]+)\\s*");
			Matcher m = p.matcher(con.getContentType());
			/*
			 * If Content-Type doesn't match this pre-conception, choose default and hope for the
			 * best.
			 */
			String charset = m.matches() ? m.group(1) : "ISO-8859-1";
			Reader r = new InputStreamReader(con.getInputStream(), charset);
			StringBuilder buf = new StringBuilder();
			while (true) {
				int ch = r.read();
				if (ch < 0)
					break;
				buf.append((char) ch);
			}
			String result = buf.toString();
			return result;
		} catch (IOException e) {
			throw new RuntimeException("failed to fetch data from " + urlSource, e);
		}
	}

	private String exchangeAuthorizationCodeForAccessToken(String authorizationCode) {
		DefaultHttpClient httpclient = new DefaultHttpClient();
		httpclient.removeRequestInterceptorByClass(RequestUserAgent.class);
		HttpPost post = new HttpPost(ACCESS_TOKEN_REQUEST_END_POINT);

		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("code", authorizationCode));
		nvps.add(new BasicNameValuePair("client_id", properties.getProperty("client_id")));
		nvps.add(new BasicNameValuePair("client_secret", properties.getProperty("client_secret")));
		nvps.add(new BasicNameValuePair("redirect_uri", properties.getProperty("redirect_uri")));
		nvps.add(new BasicNameValuePair("grant_type", "authorization_code"));

		try {
			post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			HttpResponse response = httpclient.execute(post);
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);
			JSONObject jsonObject = JSONObject.fromObject(result);
			return jsonObject.getString("access_token");
		} catch (IOException e) {
			throw new RuntimeException("failed to exchange auth code for access token", e);
		}
	}

	private String extractAuthorizationCode(HttpServletRequest request) {
		String authorizationCode = request.getParameter("code");
		return authorizationCode;
	}

	private void redirectToGoogle_accessTokenRequest(HttpServletResponse response) throws IOException {
		UrlBuilder url = new UrlBuilder(OAUTH2_END_POINT);
		url.setQueryStringParameter("response_type", "code");
		url.setQueryStringParameter("client_id", properties.getProperty("client_id"));
		url.setQueryStringParameter("redirect_uri", properties.getProperty("redirect_uri"));
		url.setQueryStringParameter("scope", properties.getProperty("scope"));
		url.setQueryStringParameter("access_type", "online");
		url.setQueryStringParameter("state", STATE);
		String destination = url.toString();
		response.sendRedirect(destination);
	}

	private boolean requestIsLogoutAttempt(HttpServletRequest request) {
		boolean result = request.getRequestURI().contains("auth/logout");
		return result;
	}

	private boolean requestIsLoginAttempt(HttpServletRequest request) {
		boolean result = request.getRequestURI().contains("auth/login");
		return result;
	}

	private boolean requestContainsAuthenticationCredentials(HttpServletRequest request) {
		String stateParameterValue = request.getParameter("state");
		return STATE.equals(stateParameterValue);
	}

	private void redirectToMainPage(HttpServletResponse arg1) throws IOException {
		String destination = properties.getProperty("url_to_main_page");
		arg1.sendRedirect(destination);
	}

	private boolean sessionIsAuthenticated(HttpSession session) {
		Object attributeValue = session.getAttribute("user");
		return attributeValue != null;
	}

	@Override
	public void init(FilterConfig arg0) throws ServletException {
		properties = new Properties();
		try {
			InputStream inputStream = GoogleAuthenticationFilter.class
					.getResourceAsStream("/google-client-id.properties");
			if (inputStream == null) {
				throw new ServletException("Failed to find google-client-id.properties");
			}
			properties.load(inputStream);
		} catch (IOException e) {
			throw new ServletException("Failed to load google-client-id.properties");
		}
	}

}
