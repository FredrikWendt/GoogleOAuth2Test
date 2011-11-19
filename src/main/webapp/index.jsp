<html>
<head>
<title>Application Main Page</title>
</head>
<body>
<h1>Welcome</h1>
<h2>To this demo app</h2>
<p>
You can do three things:
</p>
<ul>
<li>watch this page (which may include some information about you or not)</li>
<li><a href='/www-test/auth/logout'>logout</a></li>
<li><a href='/www-test/auth/login'>login</a></li>
</ul>
<hr>
<h2>State of affairs</h2>
<%
@SuppressWarnings("unchecked")
java.util.Map<String, String> user = (java.util.Map<String, String>) session.getAttribute("user");
if (user == null) {
%>
<p>You are not logged in.</p>
<%
} else {
%>
<p>Your session suggests you're logged in, with Google providing this information:</p>
<table>
<%
for (java.util.Map.Entry<String, String> entry : user.entrySet()) {
%><tr><td class='key'><%= entry.getKey() %></td><td class='value'><%= entry.getValue() %></td></tr>
<% 
}
%>
</table> 
<%
}
%>
</body>
</html>