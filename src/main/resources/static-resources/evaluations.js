// Evaluation Metrics JavaScript

// Theme toggle
document.getElementById('themeToggle').addEventListener('click', () => {
    const isDark = document.body.getAttribute('data-theme') === 'dark';
    document.body.setAttribute('data-theme', isDark ? 'light' : 'dark');
});

// Load evaluations on page load
document.addEventListener('DOMContentLoaded', () => {
    loadEvaluationMetrics();
});

async function loadEvaluationMetrics() {
    console.log('Loading evaluation metrics...');
    try {
        // Load stats
        const statsResp = await fetch('/evaluations/stats');
        console.log('Evaluation stats response status:', statsResp.status);
        if (statsResp.ok) {
            const stats = await statsResp.json();
            console.log('Evaluation stats data:', stats);
            renderEvaluationStats(stats);
        }

        // Load evaluations
        const evaluationsResp = await fetch('/evaluations');
        console.log('Evaluations response status:', evaluationsResp.status);
        if (evaluationsResp.ok) {
            const data = await evaluationsResp.json();
            console.log('Evaluations data:', data);
            renderEvaluationsTable(data.evaluations || []);
        }
    } catch (error) {
        console.error('Error loading evaluation metrics:', error);
        document.getElementById('evaluations-tbody').innerHTML =
            '<tr><td colspan="7" style="text-align:center; padding:40px; color:#c33;">Error loading evaluations</td></tr>';
    }
}

function renderEvaluationStats(stats) {
    const totalPassed = stats.allChecksPassed || 0;
    const total = stats.totalEvaluations || 0;
    const passRate = total > 0 ? ((totalPassed / total) * 100).toFixed(1) : 0;

    const statsHtml = `
        <div class="stat-card">
            <div class="stat-value">${stats.totalEvaluations}</div>
            <div class="stat-label">Total Evaluations</div>
        </div>
        <div class="stat-card success">
            <div class="stat-value">${stats.allChecksPassed}</div>
            <div class="stat-label">All Passed</div>
        </div>
        <div class="stat-card ${passRate >= 80 ? 'success' : passRate >= 60 ? 'warning' : 'danger'}">
            <div class="stat-value">${passRate}%</div>
            <div class="stat-label">Pass Rate</div>
        </div>
        <div class="stat-card">
            <div class="stat-value">${stats.summaryToxicityPassCount}</div>
            <div class="stat-label">Summary Toxicity ✓</div>
        </div>
        <div class="stat-card">
            <div class="stat-value">${stats.evidenceHallucinationPassCount}</div>
            <div class="stat-label">Evidence Hallucination ✓</div>
        </div>
        <div class="stat-card">
            <div class="stat-value">${(stats.averageToxicityConfidence * 100).toFixed(1)}%</div>
            <div class="stat-label">Avg Toxicity Confidence</div>
        </div>
    `;
    document.getElementById('evaluation-stats').innerHTML = statsHtml;
}

function renderEvaluationsTable(evaluations) {
    if (evaluations.length === 0) {
        document.getElementById('evaluations-tbody').innerHTML =
            '<tr><td colspan="7" style="text-align:center; padding:40px; color:#999;">No evaluations found</td></tr>';
        return;
    }

    const rows = evaluations.map((eval, index) => `
        <tr class="eval-row" onclick="toggleDetails('details-${index}')" style="cursor: pointer;">
            <td><strong>${eval.workflowId}</strong></td>
            <td><span class="badge ${eval.summaryToxicityPassed ? 'passed' : 'failed'}">${eval.summaryToxicityPassed ? 'PASS' : 'FAIL'}</span></td>
            <td><span class="badge ${eval.remediationToxicityPassed ? 'passed' : 'failed'}">${eval.remediationToxicityPassed ? 'PASS' : 'FAIL'}</span></td>
            <td><span class="badge ${eval.evidenceHallucinationPassed ? 'passed' : 'failed'}">${eval.evidenceHallucinationPassed ? 'PASS' : 'FAIL'}</span></td>
            <td><span class="badge ${eval.triageHallucinationPassed ? 'passed' : 'failed'}">${eval.triageHallucinationPassed ? 'PASS' : 'FAIL'}</span></td>
            <td><span class="badge ${eval.summaryHallucinationPassed ? 'passed' : 'failed'}">${eval.summaryHallucinationPassed ? 'PASS' : 'FAIL'}</span></td>
            <td>${formatDateTime(eval.evaluatedAt)}</td>
        </tr>
        <tr id="details-${index}" class="details-row" style="display: none;">
            <td colspan="7" style="background: #f8f9fa; padding: 20px;">
                <div style="margin-bottom: 15px;">
                    <strong style="font-size: 1.1em;">🛡️ Toxicity Evaluation Details:</strong>
                    <pre style="background: white; padding: 15px; border-radius: 5px; margin-top: 10px; white-space: pre-wrap; font-size: 0.9em;">${eval.toxicityExplanation || 'No explanation available'}</pre>
                </div>
                <div>
                    <strong style="font-size: 1.1em;">🔍 Hallucination Evaluation Details:</strong>
                    <pre style="background: white; padding: 15px; border-radius: 5px; margin-top: 10px; white-space: pre-wrap; font-size: 0.9em;">${eval.hallucinationExplanation || 'No explanation available'}</pre>
                </div>
                <div style="margin-top: 15px;">
                    <strong>Confidence Scores:</strong>
                    <div style="margin-top: 5px;">
                        Toxicity: ${(eval.toxicityConfidence * 100).toFixed(1)}% |
                        Hallucination: ${(eval.hallucinationConfidence * 100).toFixed(1)}%
                    </div>
                </div>
            </td>
        </tr>
    `).join('');

    document.getElementById('evaluations-tbody').innerHTML = rows;
}

function toggleDetails(rowId) {
    const detailsRow = document.getElementById(rowId);
    if (detailsRow.style.display === 'none') {
        detailsRow.style.display = 'table-row';
    } else {
        detailsRow.style.display = 'none';
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
