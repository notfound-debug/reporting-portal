<%-- session="false": a JSP creates an HTTP session by default; a public
     health check has no reason to hand out session cookies. --%>
<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- Rendered by HealthServlet. Deliberately minimal: this page is public. --%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Health · Reporting Portal</title>
</head>
<body>
<h1>status: <c:out value="${healthy ? 'UP' : 'DOWN'}"/></h1>
<ul>
    <li>database: <c:out value="${databaseUp ? 'UP' : 'DOWN'}"/></li>
    <li>warehouse views: <c:out value="${warehouseReadable ? 'READABLE' : 'NOT READABLE'}"/></li>
    <li>checked in <c:out value="${elapsedMillis}"/> ms</li>
</ul>
</body>
</html>
