<%@ page contentType="text/html; charset=UTF-8" session="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Reports">
    <section class="card">
        <h1>Reports</h1>
        <p>Logged in as <strong><c:out value="${currentUser.username}"/></strong>.</p>
    </section>
</t:layout>
