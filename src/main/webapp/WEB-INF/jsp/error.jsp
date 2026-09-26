<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%--
  The one error page (see <error-page> in web.xml). Messages are fixed text chosen
  by status code; the exception and stack trace are never shown, only logged.
  A servlet may set a more specific message in the request attribute "errorMessage".
--%>
<c:set var="status" value="${requestScope['jakarta.servlet.error.status_code']}"/>
<c:choose>
    <c:when test="${not empty errorMessage}"><c:set var="text" value="${errorMessage}"/></c:when>
    <c:when test="${status == 400}"><c:set var="text" value="The request was not valid."/></c:when>
    <c:when test="${status == 403}"><c:set var="text" value="You do not have access to this page."/></c:when>
    <c:when test="${status == 404}"><c:set var="text" value="Page not found."/></c:when>
    <c:when test="${status == 405}"><c:set var="text" value="That action is not allowed here."/></c:when>
    <c:otherwise><c:set var="text" value="Something went wrong. The error has been logged."/></c:otherwise>
</c:choose>
<t:layout title="Error">
    <section class="card">
        <h1>Error <c:out value="${status}"/></h1>
        <p class="error"><c:out value="${text}"/></p>
        <p><a href="<c:url value='/reports'/>">Back to reports</a></p>
    </section>
</t:layout>
