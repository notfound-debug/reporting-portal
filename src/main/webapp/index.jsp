<%@ page session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- "/" goes to the report list (AuthFilter has already sent anonymous users to /login). --%>
<c:redirect url="/reports"/>
