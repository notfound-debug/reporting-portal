<%@ tag description="The input fields for a report's parameters, one per ParamDef" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%--
  Used by report.jsp and chart.jsp, so both pages offer exactly the same fields.
  Reads these request attributes (set by ReportServlet / ChartPageServlet):
    report  submitted (field -> value as typed)  errors (field -> message)  states  categories
  One input per parameter type; every value printed with fn:escapeXml or c:out.
  The loop variable is "p": "param" is an EL implicit object.
--%>
            <c:forEach var="p" items="${report.params}">
                <div class="field">
                    <label for="${p.name}"><c:out value="${p.label}"/></label>
                    <c:set var="value" value="${submitted[p.name]}"/>
                    <c:choose>
                        <c:when test="${p.type == 'MONTH'}">
                            <input type="month" id="${p.name}" name="${p.name}" value="${fn:escapeXml(value)}"
                                   min="2016-01" max="2019-12">
                        </c:when>
                        <c:when test="${p.type == 'DATE'}">
                            <input type="date" id="${p.name}" name="${p.name}" value="${fn:escapeXml(value)}"
                                   min="2016-01-01" max="2019-12-31">
                        </c:when>
                        <c:when test="${p.type == 'INTEGER' or p.type == 'ROW_LIMIT'}">
                            <input type="number" id="${p.name}" name="${p.name}" value="${fn:escapeXml(value)}"
                                   min="${p.min}" max="${p.max}" step="1">
                        </c:when>
                        <c:when test="${p.type == 'STATE' or p.type == 'CHOICE'}">
                            <select id="${p.name}" name="${p.name}">
                                <option value="">All</option>
                                <c:forEach var="choice" items="${p.type == 'STATE' ? states : p.choices}">
                                    <option value="${fn:escapeXml(choice)}" ${choice == value ? 'selected' : ''}><c:out value="${choice}"/></option>
                                </c:forEach>
                            </select>
                        </c:when>
                        <c:when test="${p.type == 'CATEGORY'}">
                            <select id="${p.name}" name="${p.name}">
                                <option value="">All</option>
                                <c:forEach var="choice" items="${categories}">
                                    <option value="${fn:escapeXml(choice)}" ${choice == value ? 'selected' : ''}><c:out value="${choice}"/></option>
                                </c:forEach>
                            </select>
                        </c:when>
                        <c:when test="${p.type == 'COLUMN'}">
                            <select id="${p.name}" name="${p.name}">
                                <c:forEach var="option" items="${p.columnOptions}">
                                    <option value="${fn:escapeXml(option.key)}" ${option.key == value ? 'selected' : ''}><c:out value="${option.key}"/></option>
                                </c:forEach>
                            </select>
                        </c:when>
                        <c:when test="${p.type == 'FLAG'}">
                            <input type="checkbox" id="${p.name}" name="${p.name}" value="on" ${value == 'on' ? 'checked' : ''}>
                        </c:when>
                    </c:choose>
                    <c:if test="${not empty errors[p.name]}">
                        <div class="field-error"><c:out value="${errors[p.name]}"/></div>
                    </c:if>
                </div>
            </c:forEach>
