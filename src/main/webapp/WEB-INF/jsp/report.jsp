<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%--
  Rendered by ReportServlet. Request attributes:
    report      the ReportDefinition            submitted  field -> value as typed
    errors      field -> message (may be empty) result     ReportResult (absent if errors)
    sortLinks   column -> query string          previousLink / nextLink  query strings
  Every value is printed with c:out, fn:escapeXml or fmt:formatNumber.
  The loop variable for parameters is "p": "param" is an EL implicit object.
--%>
<fmt:setLocale value="en_US"/>
<t:layout title="${report.title}">
    <section class="card">
        <h1><c:out value="${report.title}"/></h1>
        <p class="muted"><c:out value="${report.question}"/></p>
        <c:if test="${report.hasChart}">
            <p><a href="<c:url value='/charts/${report.code}'/>">Show as a chart</a></p>
        </c:if>

        <c:if test="${not empty errors}">
            <div class="error" role="alert">
                Please correct the highlighted fields.
            </div>
        </c:if>

        <form method="get" action="<c:url value='/reports/${report.code}'/>" class="report-form">
            <t:paramFields/>

            <c:if test="${report.sortable}">
                <div class="field">
                    <label for="sort">Sort by</label>
                    <select id="sort" name="sort">
                        <c:forEach var="option" items="${report.sortableColumns}">
                            <option value="${fn:escapeXml(option.key)}" ${option.key == submitted['sort'] ? 'selected' : ''}><c:out value="${report.column(option.key).label}"/></option>
                        </c:forEach>
                    </select>
                    <c:if test="${not empty errors['sort']}"><div class="field-error"><c:out value="${errors['sort']}"/></div></c:if>
                </div>
                <div class="field">
                    <label for="dir">Direction</label>
                    <select id="dir" name="dir">
                        <option value="asc" ${submitted['dir'] == 'asc' ? 'selected' : ''}>Ascending</option>
                        <option value="desc" ${submitted['dir'] == 'desc' ? 'selected' : ''}>Descending</option>
                    </select>
                    <c:if test="${not empty errors['dir']}"><div class="field-error"><c:out value="${errors['dir']}"/></div></c:if>
                </div>
            </c:if>
            <c:if test="${not empty errors['page']}"><div class="field-error"><c:out value="${errors['page']}"/></div></c:if>

            <div class="field actions">
                <button type="submit">Run report</button>
                <a href="<c:url value='/reports/${report.code}'/>">Reset</a>
            </div>
        </form>
    </section>

    <c:if test="${not empty result}">
        <section class="card">
            <c:choose>
                <c:when test="${result.rowCount == 0}">
                    <p>No rows match these parameters.</p>
                </c:when>
                <c:otherwise>
                    <p class="muted">
                        Rows <c:out value="${firstRowNumber}"/>–<c:out value="${firstRowNumber + result.rowCount - 1}"/>
                        <c:if test="${report.paged}"> · page <c:out value="${page}"/></c:if>
                    </p>
                    <div class="table-wrap">
                        <table class="report-table">
                            <thead>
                            <tr>
                                <c:forEach var="col" items="${result.columns}">
                                    <th class="${col.type == 'TEXT' or col.type == 'DATE' or col.type == 'MONTH' ? '' : 'num'}">
                                        <c:choose>
                                            <c:when test="${not empty sortLinks[col.name]}">
                                                <a href="?${fn:escapeXml(sortLinks[col.name])}"><c:out value="${col.label}"/></a>
                                                <c:if test="${col.name == sortColumn}">
                                                    <span class="sort-mark"><c:out value="${direction == 'ASC' ? '▲' : '▼'}"/></span>
                                                </c:if>
                                            </c:when>
                                            <c:otherwise><c:out value="${col.label}"/></c:otherwise>
                                        </c:choose>
                                    </th>
                                </c:forEach>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="row" items="${result.rows}">
                                <tr>
                                    <c:forEach var="cell" items="${row}" varStatus="s">
                                        <c:set var="type" value="${result.columns[s.index].type}"/>
                                        <c:choose>
                                            <c:when test="${type == 'MONEY'}">
                                                <td class="num"><fmt:formatNumber value="${cell}" pattern="#,##0.00"/></td>
                                            </c:when>
                                            <c:when test="${type == 'INTEGER'}">
                                                <td class="num"><fmt:formatNumber value="${cell}" pattern="#,##0"/></td>
                                            </c:when>
                                            <c:when test="${type == 'PERCENT'}">
                                                <td class="num"><fmt:formatNumber value="${cell}" pattern="#,##0.0#"/></td>
                                            </c:when>
                                            <c:when test="${type == 'YEAR'}">
                                                <td class="num"><c:out value="${cell}"/></td>
                                            </c:when>
                                            <c:otherwise>
                                                <td><c:out value="${cell}"/></td>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:forEach>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>

            <c:if test="${not empty previousLink or not empty nextLink}">
                <nav class="pager">
                    <c:if test="${not empty previousLink}">
                        <a href="?${fn:escapeXml(previousLink)}">‹ Previous</a>
                    </c:if>
                    <span>Page <c:out value="${page}"/></span>
                    <c:if test="${not empty nextLink}">
                        <a href="?${fn:escapeXml(nextLink)}">Next ›</a>
                    </c:if>
                </nav>
            </c:if>
        </section>
    </c:if>
</t:layout>
