<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- Rendered by LoginServlet. session="false": JSPs never create sessions; only LoginServlet does. --%>
<t:layout title="Log in">
    <section class="card login">
        <h1>Log in</h1>
        <c:if test="${not empty message}">
            <p class="notice"><c:out value="${message}"/></p>
        </c:if>
        <c:if test="${not empty error}">
            <p class="error" role="alert"><c:out value="${error}"/></p>
        </c:if>
        <form method="post" action="<c:url value='/login'/>">
            <t:csrf/>
            <label for="username">Username</label>
            <input id="username" name="username" type="text" autocomplete="username" required
                   maxlength="30" value="${fn:escapeXml(username)}" autofocus>
            <label for="password">Password</label>
            <input id="password" name="password" type="password" autocomplete="current-password" required>
            <button type="submit">Log in</button>
        </form>
    </section>
</t:layout>
