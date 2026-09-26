/*
 * Chart pages: read the form, fetch the report's rows as JSON from
 * /api/reports/{code}, and draw them with Chart.js.
 *
 * The server does all checking (login, role, parameter validation). This script
 * only displays what it gets back. Error messages are inserted with textContent,
 * never innerHTML, so text from the server can never become HTML.
 */
(function () {
    'use strict';

    // Categorical colours in a fixed order: series 1 is always blue, 2 orange, and so on.
    // Checked with a colour-blindness validator; three of them are light on white, so every
    // chart also has a legend, hover tooltips and a link to the same data as a table.
    var SERIES_COLOURS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4'];
    var TEXT_MUTED = '#5f6b7a';
    var GRID = '#e6e9ee';

    var canvas = document.getElementById('chart');
    var form = document.getElementById('chart-form');
    var errorBox = document.getElementById('chart-errors');
    var status = document.getElementById('chart-status');

    /** The form's values as a query string, leaving out empty ones ("All" means no filter). */
    function queryString() {
        var params = new URLSearchParams();
        new FormData(form).forEach(function (value, name) {
            if (value !== '') {
                params.append(name, value);
            }
        });
        return params.toString();
    }

    function showErrors(messages) {
        errorBox.textContent = '';
        messages.forEach(function (message) {
            var line = document.createElement('div');
            line.textContent = message;
            errorBox.appendChild(line);
        });
        errorBox.hidden = false;
        status.textContent = '';
        canvas.parentNode.hidden = true;   // no empty chart area under the message
    }

    function columnIndex(columns, name) {
        for (var i = 0; i < columns.length; i++) {
            if (columns[i].name === name) {
                return i;
            }
        }
        throw new Error('No column ' + name);
    }

    function formatNumber(value) {
        return Number(value).toLocaleString('en-US', {maximumFractionDigits: 2});
    }

    function draw(body) {
        var spec = body.chart;
        var labelAt = columnIndex(body.columns, spec.labelColumn);
        var labels = body.rows.map(function (row) { return row[labelAt]; });

        var datasets = spec.series.map(function (name, i) {
            var at = columnIndex(body.columns, name);
            var colour = SERIES_COLOURS[i];
            return {
                label: body.columns[at].label,
                data: body.rows.map(function (row) { return row[at]; }),
                borderColor: spec.type === 'line' ? colour : '#ffffff',  // white gap between bar segments
                backgroundColor: colour,
                borderWidth: 2,
                borderRadius: spec.type === 'bar' ? 4 : 0,
                pointRadius: 4,
                pointHoverRadius: 6,
                tension: 0
            };
        });

        var valueAxis = {
            stacked: spec.stacked,
            beginAtZero: true,
            // With one series there is no legend, so the axis title says what is measured,
            // e.g. "2018 Q2 (R$)" when the user picked that quarter.
            title: {display: datasets.length === 1, text: datasets[0].label, color: TEXT_MUTED},
            grid: {color: GRID},
            ticks: {color: TEXT_MUTED, callback: function (value) { return formatNumber(value); }}
        };
        var labelAxis = {
            stacked: spec.stacked,
            grid: {display: false},
            ticks: {color: TEXT_MUTED}
        };

        new Chart(canvas, {
            type: spec.type,
            data: {labels: labels, datasets: datasets},
            options: {
                responsive: true,
                maintainAspectRatio: false,
                indexAxis: spec.horizontal ? 'y' : 'x',
                interaction: {mode: 'index', intersect: false},
                scales: spec.horizontal ? {x: valueAxis, y: labelAxis} : {x: labelAxis, y: valueAxis},
                plugins: {
                    // One series: the page title names it, so no legend box.
                    legend: {display: datasets.length > 1, labels: {color: TEXT_MUTED}},
                    tooltip: {
                        callbacks: {
                            label: function (item) {
                                return item.dataset.label + ': ' + formatNumber(item.raw);
                            }
                        }
                    }
                }
            }
        });

        status.textContent = body.rows.length + ' data points'
            + (body.truncated ? ' (cut off at the maximum)' : '') + '.';
    }

    function load() {
        var url = canvas.dataset.api + encodeURIComponent(canvas.dataset.report) + '?' + queryString();
        fetch(url, {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
            .then(function (response) {
                return response.json().then(function (body) {
                    return {status: response.status, body: body};
                });
            })
            .then(function (reply) {
                if (reply.status === 200) {
                    draw(reply.body);
                } else if (reply.status === 400) {
                    showErrors(Object.values(reply.body.errors));
                } else if (reply.status === 401) {
                    showErrors(['Your session has ended. Please log in again.']);
                } else if (reply.status === 403) {
                    showErrors(['You do not have access to this report.']);
                } else {
                    showErrors(['The chart data could not be loaded.']);
                }
            })
            .catch(function () {
                showErrors(['The chart data could not be loaded.']);
            });
    }

    if (typeof Chart === 'undefined') {
        showErrors(['The chart library could not be loaded (it comes from cdn.jsdelivr.net).']);
    } else {
        load();
    }
}());
