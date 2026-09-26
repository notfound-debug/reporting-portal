package com.reportingportal.report;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET /reports: the landing page after login.
 * (M2: shows who is logged in. M3 adds the list of reports the user's roles allow.)
 */
@WebServlet("/reports")
public class ReportListServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/jsp/reports.jsp").forward(request, response);
    }
}
