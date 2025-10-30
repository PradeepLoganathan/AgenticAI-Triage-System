// Incident Dashboard JavaScript

// Theme toggle
document.getElementById('themeToggle').addEventListener('click', () => {
    const isDark = document.body.getAttribute('data-theme') === 'dark';
    document.body.setAttribute('data-theme', isDark ? 'light' : 'dark');
});

// Load dashboard on page load
document.addEventListener('DOMContentLoaded', () => {
    loadIncidentDashboard();
});

async function loadIncidentDashboard() {
    console.log('Loading incident dashboard...');
    try {
        // Load stats
        const statsResp = await fetch('/dashboard/stats');
        console.log('Stats response status:', statsResp.status);
        if (statsResp.ok) {
            const stats = await statsResp.json();
            console.log('Stats data:', stats);
            renderIncidentStats(stats);
        }

        // Load incidents
        const incidentsResp = await fetch('/dashboard/incidents');
        console.log('Incidents response status:', incidentsResp.status);
        if (incidentsResp.ok) {
            const data = await incidentsResp.json();
            console.log('Incidents data:', data);
            // Handle both array and single object responses
            const incidents = Array.isArray(data) ? data : (data.incidents || [data]);
            renderIncidentsTable(incidents);
        }
    } catch (error) {
        console.error('Error loading incident dashboard:', error);
        document.getElementById('incidents-tbody').innerHTML =
            '<tr><td colspan="7" style="text-align:center; padding:40px; color:#c33;">Error loading incidents</td></tr>';
    }
}

function renderIncidentStats(stats) {
    console.log('renderIncidentStats called with:', stats);
    const statsHtml = `
        <div class="stat-card">
            <div class="stat-value">${stats.totalIncidents}</div>
            <div class="stat-label">Total Incidents</div>
        </div>
        <div class="stat-card warning">
            <div class="stat-value">${stats.activeIncidents}</div>
            <div class="stat-label">Active Incidents</div>
        </div>
        <div class="stat-card danger">
            <div class="stat-value">${stats.p1Count}</div>
            <div class="stat-label">P1 Critical</div>
        </div>
        <div class="stat-card warning">
            <div class="stat-value">${stats.p2Count}</div>
            <div class="stat-label">P2 High</div>
        </div>
        <div class="stat-card danger">
            <div class="stat-value">${stats.escalationCount}</div>
            <div class="stat-label">Escalations</div>
        </div>
        <div class="stat-card success">
            <div class="stat-value">${stats.averageProgress.toFixed(1)}</div>
            <div class="stat-label">Avg Progress</div>
        </div>
    `;
    const element = document.getElementById('incident-stats');
    console.log('incident-stats element:', element);
    if (element) {
        element.innerHTML = statsHtml;
        console.log('Stats HTML updated successfully');
    } else {
        console.error('incident-stats element not found!');
    }
}

function renderIncidentsTable(incidents) {
    console.log('renderIncidentsTable called with:', incidents);
    if (incidents.length === 0) {
        document.getElementById('incidents-tbody').innerHTML =
            '<tr><td colspan="7" style="text-align:center; padding:40px; color:#999;">No incidents found</td></tr>';
        return;
    }

    const rows = incidents.map(incident => `
        <tr>
            <td><strong>${incident.incidentId}</strong></td>
            <td>${incident.service}</td>
            <td><span class="badge ${incident.severity.toLowerCase()}">${incident.severity}</span></td>
            <td><span class="badge ${incident.isActive ? 'active' : 'completed'}">${incident.status}</span></td>
            <td>${incident.stepProgress}/7</td>
            <td>${incident.assignedTeam}</td>
            <td>${formatDateTime(incident.startTime)}</td>
        </tr>
    `).join('');

    const tbody = document.getElementById('incidents-tbody');
    console.log('incidents-tbody element:', tbody);
    if (tbody) {
        tbody.innerHTML = rows;
        console.log('Incidents table updated successfully with', incidents.length, 'rows');
    } else {
        console.error('incidents-tbody element not found!');
    }
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    try {
        const date = new Date(dateTimeStr);
        return date.toLocaleString('en-US', {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return dateTimeStr;
    }
}
