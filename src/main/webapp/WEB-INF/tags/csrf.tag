<%@ tag description="Hidden CSRF token field; put it inside every POST form" pageEncoding="UTF-8" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<input type="hidden" name="_csrf" value="${fn:escapeXml(csrfToken)}">
