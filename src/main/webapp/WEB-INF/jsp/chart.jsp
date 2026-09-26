<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%--
  Rendered by ChartPageServlet. The page holds the form and an empty canvas;
  static/js/charts.js reads the form, fetches /api/reports/{code} and draws the chart.
  No inline script (the Content-Security-Policy forbids it): the script finds the
  report code and API address in data- attributes.
  Changing the form reloads this page with the new parameters in the URL (GET).
--%>
<t:layout title="${report.title} (chart)" script="charts.js">
    <section class="card">
        <h1><c:out value="${report.title}"/></h1>
        <p class="muted"><c:out value="${report.question}"/></p>
        <p><a href="<c:url value='/reports/${report.code}'/>">Show the same data as a table</a></p>

        <form method="get" action="<c:url value='/charts/${report.code}'/>" class="report-form" id="chart-form">
            <t:paramFields/>
            <div class="field actions">
                <button type="submit">Update chart</button>
                <a href="<c:url value='/charts/${report.code}'/>">Reset</a>
            </div>
        </form>
    </section>

    <section class="card">
        <div id="chart-errors" class="error" role="alert" hidden></div>
        <p id="chart-status" class="muted">Loading…</p>
        <div class="chart-box">
            <canvas id="chart"
                    data-report="<c:out value='${report.code}'/>"
                    data-api="<c:url value='/api/reports/'/>"
                    role="img" aria-label="<c:out value='${report.title}'/> chart"></canvas>
        </div>
    </section>

    <%-- Chart.js 4.5.1 from the jsDelivr CDN, pinned by version and by hash (Subresource
         Integrity): if the file on the CDN ever changed, the browser would refuse to run it. --%>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.5.1/dist/chart.umd.min.js"
            integrity="sha384-jb8JQMbMoBUzgWatfe6COACi2ljcDdZQ2OxczGA3bGNeWe+6DChMTBJemed7ZnvJ"
            crossorigin="anonymous"></script>
</t:layout>
