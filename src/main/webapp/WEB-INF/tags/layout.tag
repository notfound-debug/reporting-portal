<%@ tag description="Page frame shared by every portal page: head, top bar, main area" pageEncoding="UTF-8" %>
<%@ attribute name="title" required="true" %>
<%@ attribute name="script" required="false" description="Optional extra script under /static/js, e.g. charts.js" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%--
  Escaping rule used on every page: data is printed only through <c:out> (or
  fn:escapeXml inside an attribute). A bare ${...} in the page text is NOT escaped.
  currentUser and csrfToken are request attributes set by AuthFilter / CsrfFilter.
--%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${title}"/> · Reporting Portal</title>
    <link rel="stylesheet" href="<c:url value='/static/css/portal.css'/>">
</head>
<body>
<header class="topbar">
    <a class="brand" href="<c:url value='/reports'/>">Reporting Portal</a>
    <c:if test="${not empty currentUser}">
        <nav class="nav">
            <a href="<c:url value='/reports'/>">Reports</a>
            <a href="<c:url value='/exports'/>">Exports</a>
        </nav>
        <div class="who">
            <span><c:out value="${currentUser.displayName}"/></span>
            <span class="roles"><c:forEach var="role" items="${currentUser.roles}"><span class="role"><c:out value="${role}"/></span></c:forEach></span>
            <form method="post" action="<c:url value='/logout'/>" class="inline">
                <t:csrf/>
                <button type="submit" class="link-button">Log out</button>
            </form>
        </div>
    </c:if>
</header>
<main class="main">
    <jsp:doBody/>
</main>
<c:if test="${not empty script}">
    <script src="<c:url value='/static/js/${script}'/>"></script>
</c:if>
</body>
</html>
