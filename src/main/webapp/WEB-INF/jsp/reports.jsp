<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%-- Rendered by ReportListServlet. "reports" holds only what this user's roles allow. --%>
<t:layout title="Reports">
    <h1>Reports</h1>
    <p class="muted">
        <c:out value="${fn:length(reports)}"/> reports available to
        <c:out value="${currentUser.displayName}"/>.
    </p>
    <div class="report-grid">
        <c:forEach var="report" items="${reports}">
            <article class="card report-card">
                <h2>
                    <a href="<c:url value='/reports/${report.code}'/>"><c:out value="${report.title}"/></a>
                </h2>
                <p><c:out value="${report.question}"/></p>
                <p class="report-links">
                    <a href="<c:url value='/reports/${report.code}'/>">Table</a>
                    <c:if test="${report.hasChart}">
                        · <a href="<c:url value='/charts/${report.code}'/>">Chart</a>
                    </c:if>
                    <span class="muted code"><c:out value="${report.view}"/></span>
                </p>
            </article>
        </c:forEach>
    </div>
</t:layout>
