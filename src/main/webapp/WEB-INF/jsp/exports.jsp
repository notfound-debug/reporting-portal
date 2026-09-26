<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%--
  Rendered by ExportServlet. Request attributes:
    schedules, runs     lists (the user's own; everyone's for an admin)
    reportTitles        report code -> title        showOwner  true for an admin
    timeZone            PORTAL_TIME_ZONE; times are stored in UTC and shown in this zone
    message / formError fixed text chosen by the servlet
  Buttons that change something are POST forms with the CSRF token.
--%>
<fmt:setLocale value="en_US"/>
<t:layout title="Exports">
    <h1>Scheduled CSV exports</h1>
    <p class="muted">
        Create a schedule from any report page ("Schedule a daily CSV export"). Times are in
        <c:out value="${timeZone}"/>.
    </p>
    <c:if test="${not empty message}"><p class="notice"><c:out value="${message}"/></p></c:if>
    <c:if test="${not empty formError}"><p class="error" role="alert"><c:out value="${formError}"/></p></c:if>

    <section class="card">
        <h2>Schedules</h2>
        <c:choose>
            <c:when test="${empty schedules}">
                <p>No schedules yet.</p>
            </c:when>
            <c:otherwise>
                <div class="table-wrap">
                    <table class="report-table wrap">
                        <thead>
                        <tr>
                            <th class="num">#</th>
                            <th>Report</th>
                            <c:if test="${showOwner}"><th>Owner</th></c:if>
                            <th>Parameters</th>
                            <th>Daily at</th>
                            <th>Next run</th>
                            <th>Status</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="s" items="${schedules}">
                            <tr>
                                <td class="num"><c:out value="${s.scheduleId}"/></td>
                                <td><c:out value="${reportTitles[s.reportCode]}"/></td>
                                <c:if test="${showOwner}"><td><c:out value="${s.ownerUsername}"/></td></c:if>
                                <td class="params"><c:out value="${s.paramsQuery}"/></td>
                                <td><c:out value="${s.runTime}"/></td>
                                <td>
                                    <c:if test="${s.enabled}">
                                        <fmt:formatDate value="${s.nextRunAt}" pattern="yyyy-MM-dd HH:mm" timeZone="${timeZone}"/>
                                    </c:if>
                                </td>
                                <td><c:out value="${s.enabled ? 'Enabled' : 'Disabled'}"/></td>
                                <td class="actions-cell">
                                    <c:if test="${s.enabled}">
                                        <form method="post" action="<c:url value='/exports'/>" class="inline">
                                            <t:csrf/>
                                            <input type="hidden" name="action" value="runNow">
                                            <input type="hidden" name="schedule_id" value="${s.scheduleId}">
                                            <button type="submit" class="small">Run now</button>
                                        </form>
                                        <form method="post" action="<c:url value='/exports'/>" class="inline">
                                            <t:csrf/>
                                            <input type="hidden" name="action" value="disable">
                                            <input type="hidden" name="schedule_id" value="${s.scheduleId}">
                                            <button type="submit" class="small secondary">Disable</button>
                                        </form>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

    <section class="card">
        <h2>Recent runs</h2>
        <c:choose>
            <c:when test="${empty runs}">
                <p>No exports have run yet.</p>
            </c:when>
            <c:otherwise>
                <div class="table-wrap">
                    <table class="report-table wrap">
                        <thead>
                        <tr>
                            <th class="num">Run</th>
                            <th class="num">Schedule</th>
                            <th>Report</th>
                            <c:if test="${showOwner}"><th>Owner</th></c:if>
                            <th>Started</th>
                            <th>Status</th>
                            <th class="num">Rows</th>
                            <th class="num">Size</th>
                            <th>File or reason</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="r" items="${runs}">
                            <tr>
                                <td class="num"><c:out value="${r.runId}"/></td>
                                <td class="num"><c:out value="${r.scheduleId}"/></td>
                                <td><c:out value="${reportTitles[r.reportCode]}"/></td>
                                <c:if test="${showOwner}"><td><c:out value="${r.ownerUsername}"/></td></c:if>
                                <td><fmt:formatDate value="${r.startedAt}" pattern="yyyy-MM-dd HH:mm:ss" timeZone="${timeZone}"/></td>
                                <td><span class="status status-${r.status}"><c:out value="${r.status}"/></span></td>
                                <td class="num"><fmt:formatNumber value="${r.rowCount}" pattern="#,##0"/></td>
                                <td class="num">
                                    <c:if test="${not empty r.fileBytes}"><fmt:formatNumber value="${r.fileBytes / 1024}" pattern="#,##0.0"/> KB</c:if>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${r.success}">
                                            <a href="<c:url value='/exports/download'><c:param name='run' value='${r.runId}'/></c:url>"><c:out value="${r.fileName}"/></a>
                                        </c:when>
                                        <c:otherwise><span class="muted"><c:out value="${r.errorMessage}"/></span></c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>
</t:layout>
