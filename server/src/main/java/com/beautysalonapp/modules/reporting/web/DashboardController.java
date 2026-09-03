package com.beautysalonapp.modules.reporting.web;

import com.beautysalonapp.modules.reporting.application.ReportService;
import com.beautysalonapp.modules.reporting.application.ReportService.DailyDashboard;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final ReportService reports;

    public DashboardController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/today")
    public DailyDashboard today() {
        return reports.today();
    }

    @GetMapping("/end-of-day")
    @PreAuthorize("hasAuthority('REPORTING_VIEW')")
    public String endOfDay() {
        return reports.endOfDaySummary();
    }
}
