        let currentTriageId = null;
        let startTime = null;
        let pollingInterval = null;

        // Demo scenarios
        const scenarios = {
            'payment-outage': {
                description: `Payment service is completely down since 14:30 UTC. Users are unable to complete transactions and getting 503 errors. Multiple customer complaints received via social media and support tickets. Revenue impact is significant.

Error details:
- Payment gateway returning 503 Service Unavailable
- Database connection timeouts in payment-service logs  
- CPU usage spiked to 95% on payment-db-primary
- Recent deployment of payment-service v2.1.4 at 14:25 UTC
- Load balancer health checks failing for 3/6 payment service instances

Customer impact: 
- 100% of payment transactions failing
- Estimated $50K/hour revenue loss
- 847 support tickets created in last hour`
            },
            'database-slow': {
                description: `Database performance degradation noticed since 08:00 UTC. Query response times increased from average 50ms to 2-5 seconds. Intermittent timeouts reported by multiple services.

Technical details:
- Primary database CPU at 85% consistently  
- Disk I/O wait time increased to 40%
- Slow query log showing expensive JOIN operations
- Connection pool exhaustion warnings
- No recent deployments or configuration changes

Service impact:
- checkout-service: 30% slower response times
- user-service: occasional timeout errors  
- search-service: degraded performance
- Overall user experience degraded but functional`
            },
            'auth-errors': {
                description: `Authentication service experiencing intermittent failures. Users reporting login issues and getting "Invalid credentials" errors for valid accounts.

Symptoms:
- 15% of login attempts failing with HTTP 401
- JWT token validation intermittently failing
- auth-service logs showing Redis connection errors
- Session data inconsistency reported

Infrastructure:
- Redis cluster showing node disconnection events
- Network latency spikes between auth-service and Redis
- Recent security patch applied to auth-service this morning
- Load increased 40% due to retry attempts

Impact:
- Users unable to access accounts intermittently
- Mobile app login success rate dropped to 85%
- Customer support receiving 200+ login-related tickets`
            },
            'deployment-issue': {
                description: `Issues reported after deploying inventory-service v1.8.2 at 16:45 UTC. Some product pages showing incorrect stock levels and "out of stock" errors for available items.

Deployment details:
- inventory-service v1.8.2 deployed via blue-green deployment  
- Database migration included schema changes to inventory table
- New caching layer introduced for stock level queries
- Deployment completed successfully without rollback

Issues observed:
- 5% of products showing incorrect stock levels
- Cache invalidation not working properly for stock updates
- Inventory sync job failing for some SKUs  
- Minor impact on conversion rates

Monitoring:
- No error rate increase in logs
- All health checks passing
- Performance metrics within normal ranges
- Feature flags available for rollback`
            }
        };

        // Load demo scenario
        function loadScenario(scenarioKey) {
            const scenario = scenarios[scenarioKey];
            if (scenario) {
                document.getElementById('incident').value = scenario.description;
            }
        }

        // Simple control to grow context for demo purposes
        async function growContext(times = 5) {
            if (!currentTriageId) return;
            try {
                const resp = await fetch(`/triage/${currentTriageId}/state`);
                if (!resp.ok) return;
                const state = await resp.json();
                const okStatuses = ['SUMMARY_READY', 'COMPLETED'];
                if (!okStatuses.includes(state.status)) {
                    showError('Please wait until triage reaches Summary or Completed before adding notes.');
                    return;
                }
                await fetch(`/triage/${currentTriageId}/repeat`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: 'Context growth ping', times })
                });
                updateDetailedState();
            } catch (e) {
                console.error('growContext failed', e);
            }
        }

        // Generate unique triage ID
        function generateTriageId() {
            const timestamp = new Date().toISOString().replace(/[-:.]/g, '').slice(0, 15);
            const random = Math.random().toString(36).substring(2, 6);
            return `TRIAGE-${timestamp}-${random}`;
        }

        // Initialize page
        document.addEventListener('DOMContentLoaded', function() {
            // Generate initial triage ID
            const triageId = generateTriageId();
            document.getElementById('triageId').value = triageId;
            
            // Load default scenario
            loadScenario('payment-outage');

            // Attach copy buttons to all code blocks
            try {
                document.querySelectorAll('.input-output').forEach(section => {
                    const title = section.querySelector('h4');
                    const block = section.querySelector('.code-block');
                    if (title && block) {
                        const btn = document.createElement('button');
                        btn.className = 'btn-copy';
                        btn.textContent = 'Copy';
                        btn.addEventListener('click', () => {
                            navigator.clipboard.writeText(block.textContent || '');
                            showSuccess('Copied to clipboard');
                        });
                        title.appendChild(btn);
                    }
                });
            } catch (e) { console.warn('Copy buttons init failed', e); }
            
            // Toolbar buttons
            document.getElementById('themeToggle').addEventListener('click', () => {
                const isDark = document.body.getAttribute('data-theme') === 'dark';
                document.body.setAttribute('data-theme', isDark ? 'light' : 'dark');
            });
            document.getElementById('restartBtn').addEventListener('click', () => restartWorkflow());
            document.getElementById('exportTopBtn').addEventListener('click', () => exportResults());

            // Collapsible cards
            document.querySelectorAll('.agent-card .collapse-btn').forEach(btn => {
                btn.addEventListener('click', (e) => {
                    const card = e.target.closest('.agent-card');
                    if (card) card.classList.toggle('collapsed');
                });
            });
            // Allow clicking header to collapse/expand
            document.querySelectorAll('.agent-card .agent-header').forEach(h => {
                h.addEventListener('dblclick', (e) => {
                    const card = e.currentTarget.closest('.agent-card');
                    if (card) card.classList.toggle('collapsed');
                });
            });

            // Start with cards collapsed by default
            document.querySelectorAll('.agent-card').forEach(card => card.classList.add('collapsed'));
        });

        // Restart workflow UI
        function restartWorkflow() {
            resetUI();
            document.getElementById('incident').value = '';
            document.getElementById('triageId').value = '';
            resetAgentCards();
            resetWorkflowProgress();
            document.getElementById('metricsPanel').style.display = 'grid';
            document.getElementById('currentSeverity').textContent = '-';
            document.getElementById('completedSteps').textContent = '0/7';
            document.getElementById('totalTime').textContent = '0s';
            document.getElementById('conversationTimeline').style.display = 'none';
            startTime = null;
            currentTriageId = null;
        }

        // Form submission
        document.getElementById('triageForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const triageId = document.getElementById('triageId').value || generateTriageId();
            const incident = document.getElementById('incident').value;

            if (!incident.trim()) {
                alert('Please enter an incident description');
                return;
            }

            currentTriageId = triageId;
            startTime = Date.now();
            
            // Update UI for workflow start
            document.getElementById('startBtn').disabled = true;
            document.getElementById('startBtn').innerHTML = '<div class="spinner"></div> Starting Workflow...';
            document.getElementById('metricsPanel').style.display = 'grid';

            // Reset all agent cards
            resetAgentCards();
            resetWorkflowProgress();

            try {
                // Start the triage workflow
                const response = await fetch(`/triage/${triageId}`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        incident: incident
                    })
                });

                if (response.ok) {
                    // Start polling for updates
                    startPolling();
                } else {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }

            } catch (error) {
                console.error('Error starting workflow:', error);
                showError('Failed to start workflow: ' + error.message);
                resetUI();
            }
        });

        // Reset UI state
        function resetUI() {
            document.getElementById('startBtn').disabled = false;
            document.getElementById('startBtn').innerHTML = '🚀 Start Triage Workflow';
            if (pollingInterval) {
                clearInterval(pollingInterval);
                pollingInterval = null;
            }
        }

        // Reset agent cards
        function resetAgentCards() {
            const agents = ['classifier', 'evidence', 'triage', 'knowledge', 'remediation', 'summary'];
            agents.forEach(agent => {
                const card = document.getElementById(`${agent}-card`);
                const status = card.querySelector('.agent-status');
                card.className = 'agent-card collapsed';
                status.textContent = 'Waiting...';
                
                document.getElementById(`${agent}-input`).textContent = 'Waiting...';
                document.getElementById(`${agent}-output`).textContent = 'No output yet';
            });
        }

        // Reset workflow progress
        function resetWorkflowProgress() {
            const steps = document.querySelectorAll('.step');
            steps.forEach(step => {
                step.className = 'step';
            });
        }

        let consecutiveErrors = 0;
        let lastProgressTime = Date.now();

        // Start polling for updates
        function startPolling() {
            pollingInterval = setInterval(async () => {
                try {
                    await updateWorkflowStatus();
                    await updateDetailedState();
                    
                    // Reset error counter on success
                    consecutiveErrors = 0;
                    
                    // Check for timeout (no progress for 5 minutes)
                    if (Date.now() - lastProgressTime > 300000) {
                        showError('Workflow appears to be stuck. Consider restarting.');
                        clearInterval(pollingInterval);
                        resetUI();
                    }
                    
                } catch (error) {
                    console.error('Polling error:', error);
                    consecutiveErrors++;
                    
                    if (consecutiveErrors >= 5) {
                        showError('Multiple polling failures. Workflow may have failed.');
                        clearInterval(pollingInterval);
                        resetUI();
                    }
                }
            }, 2000); // Poll every 2 seconds
        }

        // Update workflow status
        async function updateWorkflowStatus() {
            if (!currentTriageId) return;

            try {
                const response = await fetch(`/triage/${currentTriageId}`);
                if (response.ok) {
                    const conversations = await response.json();
                    updateFromConversations(conversations);
                }
            } catch (error) {
                console.error('Error fetching conversations:', error);
            }
        }

        // Update detailed state
        async function updateDetailedState() {
            if (!currentTriageId) return;

            try {
                const response = await fetch(`/triage/${currentTriageId}/state`);
                if (response.ok) {
                    const state = await response.json();
                    updateFromState(state);
                    updateMemoryPanel(state);
                }
            } catch (error) {
                console.error('Error fetching state:', error);
            }
        }

        // Update UI from conversations
        function updateFromConversations(conversations) {
            // Update metrics
            updateMetrics();

            let currentStep = '';
            let completedSteps = [];

            // Process conversations to understand current state and active step
            conversations.forEach((conv, index) => {
                if (conv.role === 'assistant') {
                    const content = conv.content.toLowerCase();
                    const timestamp = new Date().toLocaleTimeString();
                    
                    if (content.includes('classification completed')) {
                        completedSteps.push('classify');
                        updateStepStatus('classify', 'completed');
                        updateAgentStatus('classifier', 'completed', `Completed at ${timestamp}`);
                        expandCard('classifier');
                        if (!completedSteps.includes('evidence')) currentStep = 'evidence';
                        lastProgressTime = Date.now();
                    } else if (content.includes('evidence analysis completed')) {
                        completedSteps.push('evidence');
                        updateStepStatus('evidence', 'completed');
                        updateAgentStatus('evidence', 'completed', `Completed at ${timestamp}`);
                        expandCard('evidence');
                        if (!completedSteps.includes('triage')) currentStep = 'triage';
                        lastProgressTime = Date.now();
                    } else if (content.includes('triage analysis completed')) {
                        completedSteps.push('triage');
                        updateStepStatus('triage', 'completed');
                        updateAgentStatus('triage', 'completed', `Completed at ${timestamp}`);
                        expandCard('triage');
                        if (!completedSteps.includes('knowledge')) currentStep = 'knowledge';
                        lastProgressTime = Date.now();
                    } else if (content.includes('knowledge base search completed.')) {
                        completedSteps.push('knowledge');
                        updateStepStatus('knowledge', 'completed');
                        updateAgentStatus('knowledge', 'completed', `Completed at ${timestamp}`);
                        expandCard('knowledge');
                        if (!completedSteps.includes('remediate')) currentStep = 'remediate';
                        lastProgressTime = Date.now();
                    } else if (content.includes('remediation plan completed')) {
                        completedSteps.push('remediate');
                        updateStepStatus('remediate', 'completed');
                        updateAgentStatus('remediation', 'completed', `Completed at ${timestamp}`);
                        expandCard('remediation');
                        if (!completedSteps.includes('summarize')) currentStep = 'summarize';
                        lastProgressTime = Date.now();
                    } else if (content.includes('summaries completed')) {
                        completedSteps.push('summarize');
                        updateStepStatus('summarize', 'completed');
                        updateAgentStatus('summary', 'completed', `Completed at ${timestamp}`);
                        expandCard('summary');
                        currentStep = 'complete';
                        lastProgressTime = Date.now();
                    } else if (content.includes('workflow completed')) {
                        updateStepStatus('complete', 'completed');
                        completeWorkflow();
                        lastProgressTime = Date.now();
                        return;
                    }
                }
            });

            // Update active step indicator
            if (currentStep && !completedSteps.includes(currentStep)) {
                updateStepStatus(currentStep, 'active');
                const agentMap = {
                    'classify': 'classifier',
                    'evidence': 'evidence', 
                    'triage': 'triage',
                    'knowledge': 'knowledge',
                    'remediate': 'remediation',
                    'summarize': 'summary'
                };
                if (agentMap[currentStep]) {
                    updateAgentStatus(agentMap[currentStep], 'active', 'Processing...');
                    expandCard(agentMap[currentStep]);
                }
            }

            // Display conversation timeline
            updateConversationTimeline(conversations);
        }

        // Update UI from detailed state
        function updateFromState(state) {
            const incident = document.getElementById('incident').value;

            // Update classifier
            if (state.classificationJson) {
                document.getElementById('classifier-input').textContent = 
                    `Incident: ${incident.substring(0, 200)}...`;
                document.getElementById('classifier-output').textContent = 
                    formatJSON(state.classificationJson);
                updateAgentStatus('classifier', 'completed', 'Classification completed');
                expandCard('classifier');
                
                // Extract severity for metrics
                try {
                    const classification = JSON.parse(state.classificationJson);
                    const severity = classification.classification?.severity || classification.severity || 'Unknown';
                    document.getElementById('currentSeverity').textContent = severity;
                } catch (e) {
                    // Handle non-JSON response
                    if (state.classificationJson.includes('P1')) {
                        document.getElementById('currentSeverity').textContent = 'P1';
                    } else if (state.classificationJson.includes('P2')) {
                        document.getElementById('currentSeverity').textContent = 'P2';
                    }
                }
            }

            // Update evidence
            if (state.evidenceLogs) {
                document.getElementById('evidence-input').textContent = 
                    `Service: ${extractServiceName(state.classificationJson)}\nMetrics: errors:rate5m\nTime Range: 1h`;
                document.getElementById('evidence-output').textContent = 
                    formatJSON(state.evidenceLogs);
                updateAgentStatus('evidence', 'completed', 'Evidence gathered');
                expandCard('evidence');
            }

            // Update triage
            if (state.triageText) {
                document.getElementById('triage-input').textContent = 
                    `Incident: ${incident.substring(0, 100)}...\nClassification: ${state.classificationJson?.substring(0, 100)}...\nEvidence: ${state.evidenceLogs?.substring(0, 100)}...`;
                document.getElementById('triage-output').textContent = 
                    formatJSON(state.triageText);
                updateAgentStatus('triage', 'completed', 'Triage completed');
                expandCard('triage');
            }

            // Update knowledge base
            if (state.knowledgeBaseResult) {
                document.getElementById('knowledge-input').textContent = 
                    `Service: ${extractServiceName(state.classificationJson)}\nSearching runbooks and documentation...`;
                document.getElementById('knowledge-output').textContent = 
                    formatJSON(state.knowledgeBaseResult);
                updateAgentStatus('knowledge', 'completed', 'Knowledge base search completed');
                expandCard('knowledge');
            }

            // Update remediation
            if (state.remediationText) {
                document.getElementById('remediation-input').textContent = 
                    `All previous context:\n- Incident\n- Classification\n- Evidence\n- Triage Analysis\n- Knowledge Base Results`;
                document.getElementById('remediation-output').textContent = 
                    formatJSON(state.remediationText);
                updateAgentStatus('remediation', 'completed', 'Remediation planned');
                expandCard('remediation');
            }

            // Update summary
            if (state.summaryText) {
                document.getElementById('summary-input').textContent = 
                    `Complete incident context for multi-audience summary generation`;
                document.getElementById('summary-output').textContent = 
                    formatJSON(state.summaryText);
                updateAgentStatus('summary', 'completed', 'Summary completed');
                expandCard('summary');
            }

            // Update status based on workflow state
            if (state.status === 'TRIAGED' && !state.knowledgeBaseResult) {
                updateAgentStatus('knowledge', 'active', 'Searching knowledge base...');
                updateStepStatus('knowledge', 'active');
            } else if (state.status === 'KNOWLEDGE_BASE_SEARCHED' && state.knowledgeBaseResult && !document.getElementById('knowledge-card').classList.contains('completed')) {
                updateAgentStatus('knowledge', 'completed', 'Knowledge base search completed');
                updateStepStatus('knowledge', 'completed');
            }

            // Check if workflow is complete
            if (state.status === 'COMPLETED') {
                completeWorkflow();
            }
        }

        function updateMemoryPanel(state) {
            if (!state) return;
            const fmtMB = (n) => {
                if (!n || n <= 0) return '0 MB';
                return `${(n / (1024*1024)).toFixed(1)} MB`;
            };
            const shortId = (id) => {
                if (!id) return '-';
                return id.length > 8 ? id.substring(0, 8) + '…' : id;
            };

            if (typeof state.contextEntries === 'number') {
                document.getElementById('contextEntries').textContent = state.contextEntries;
            }
            if (typeof state.approxStateChars === 'number') {
                document.getElementById('stateSize').textContent = state.approxStateChars.toLocaleString();
            }
            if (typeof state.heapUsedBytes === 'number') {
                document.getElementById('heapUsed').textContent = fmtMB(state.heapUsedBytes);
            }
            if (state.agentSessionId) {
                document.getElementById('sessionIdShort').textContent = shortId(state.agentSessionId);
            }
        }

        // Extract service name from classification
        function extractServiceName(classificationJson) {
            if (!classificationJson) return 'unknown';
            try {
                const classification = JSON.parse(classificationJson);
                return classification.classification?.service || classification.service || 'unknown';
            } catch (e) {
                // Try regex extraction
                const match = classificationJson.match(/"service":\s*"([^"]+)"/);
                return match ? match[1] : 'unknown';
            }
        }

        // Format JSON for display
        function formatJSON(text) {
            if (!text) return 'No output yet';
            
            try {
                const parsed = JSON.parse(text);
                return JSON.stringify(parsed, null, 2);
            } catch (e) {
                // Not valid JSON, return as-is but truncated
                return text.length > 5000 ? text.substring(0, 5000) + '\n...[truncated]' : text;
            }
        }

        // Update step status
        function updateStepStatus(stepName, status) {
            const step = document.querySelector(`[data-step="${stepName}"]`);
            if (step) {
                step.className = `step ${status}`;
            }
        }

        // Update agent status
        function updateAgentStatus(agentName, status, message) {
            const card = document.getElementById(`${agentName}-card`);
            const statusEl = card.querySelector('.agent-status');
            
            card.className = `agent-card ${status}`;
            statusEl.textContent = message;
            
            if (status === 'active') {
                statusEl.innerHTML = '<div class="loading"><div class="spinner"></div>' + message + '</div>';
            }
        }

        // Helpers to control card collapse state
        function expandCard(agentName) {
            const card = document.getElementById(`${agentName}-card`);
            if (card) card.classList.remove('collapsed');
        }
        function collapseCard(agentName) {
            const card = document.getElementById(`${agentName}-card`);
            if (card) card.classList.add('collapsed');
        }

        // Update metrics
        function updateMetrics() {
            if (startTime) {
                const elapsed = Math.floor((Date.now() - startTime) / 1000);
                document.getElementById('totalTime').textContent = `${elapsed}s`;
            }

            // Count completed steps
            const completedSteps = document.querySelectorAll('.step.completed').length;
            document.getElementById('completedSteps').textContent = `${completedSteps}/7`;
        }

        // Update conversation timeline
        function updateConversationTimeline(conversations) {
            const timeline = document.getElementById('conversationTimeline');
            const timelineContent = document.getElementById('timelineContent');
            
            if (conversations && conversations.length > 0) {
                timeline.style.display = 'block';
                
                timelineContent.innerHTML = '';
                conversations.forEach(conv => {
                    const entry = document.createElement('div');
                    entry.className = `timeline-entry ${conv.role}`;
                    
                    const icon = getTimelineIcon(conv.role);
                    const roleLabel = conv.role.charAt(0).toUpperCase() + conv.role.slice(1);
                    
                    entry.innerHTML = `
                        <div class="timeline-icon">${icon}</div>
                        <div class="timeline-content">
                            <div class="timeline-role">${roleLabel}</div>
                            <div class="timeline-message">${conv.content}</div>
                            <div class="timeline-time">Step ${conversations.indexOf(conv) + 1}</div>
                        </div>
                    `;
                    
                    timelineContent.appendChild(entry);
                });
            }
        }

        // Get timeline icon based on role
        function getTimelineIcon(role) {
            switch(role) {
                case 'system': return '⚙️';
                case 'user': return '👤';
                case 'assistant': return '🤖';
                default: return '💬';
            }
        }

        // Export results
        function exportResults() {
            if (!currentTriageId) return;

            // Gather all data for export
            const exportData = {
                triageId: currentTriageId,
                timestamp: new Date().toISOString(),
                duration: startTime ? Math.floor((Date.now() - startTime) / 1000) : 0,
                incident: document.getElementById('incident').value,
                results: {
                    classification: document.getElementById('classifier-output').textContent,
                    evidence: document.getElementById('evidence-output').textContent,
                    triage: document.getElementById('triage-output').textContent,
                    knowledgeBase: document.getElementById('knowledge-output').textContent,
                    remediation: document.getElementById('remediation-output').textContent,
                    summary: document.getElementById('summary-output').textContent
                },
                timeline: Array.from(document.querySelectorAll('.timeline-entry')).map(entry => ({
                    role: entry.querySelector('.timeline-role').textContent.toLowerCase(),
                    content: entry.querySelector('.timeline-message').textContent,
                    step: entry.querySelector('.timeline-time').textContent
                }))
            };

            // Create and download file
            const blob = new Blob([JSON.stringify(exportData, null, 2)], {
                type: 'application/json'
            });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `triage-${currentTriageId}-${new Date().toISOString().slice(0, 19)}.json`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);

            showSuccess('Results exported successfully!');
        }

        // Complete workflow
        function completeWorkflow() {
            updateStepStatus('complete', 'completed');
            resetUI();
            
            // Show export button
            document.getElementById('exportBtn').style.display = 'block';
            
            // Show completion message
            setTimeout(() => {
                showSuccess('Workflow completed successfully! All agents have processed the incident.');
            }, 1000);
        }

        // Show error message
        function showError(message) {
            const errorDiv = document.createElement('div');
            errorDiv.className = 'error';
            errorDiv.textContent = message;
            errorDiv.style.margin = '20px 0';
            
            const form = document.getElementById('triageForm');
            form.insertBefore(errorDiv, form.firstChild);
            
            setTimeout(() => errorDiv.remove(), 5000);
        }

        // Show success message
        function showSuccess(message) {
            const successDiv = document.createElement('div');
            successDiv.className = 'success';
            successDiv.textContent = message;
            successDiv.style.margin = '20px 0';
            
            const form = document.getElementById('triageForm');
            form.insertBefore(successDiv, form.firstChild);
            
            setTimeout(() => successDiv.remove(), 8000);
        }

        // =======================
        // Tab Navigation
        // =======================

        function switchTab(tabName) {
            // Hide all tab contents
            document.querySelectorAll('.tab-content').forEach(tab => {
                tab.classList.remove('active');
            });

            // Remove active class from all tab buttons
            document.querySelectorAll('.tab-btn').forEach(btn => {
                btn.classList.remove('active');
            });

            // Show selected tab
            document.getElementById(`${tabName}-tab`).classList.add('active');

            // Highlight selected button
            event.target.classList.add('active');

            // Load data for the selected tab
            if (tabName === 'incidents') {
                loadIncidentDashboard();
            } else if (tabName === 'evaluations') {
                loadEvaluationMetrics();
            }
        }

        // =======================
        // Incident Dashboard
        // =======================

        async function loadIncidentDashboard() {
            try {
                // Load stats
                const statsResp = await fetch('/dashboard/stats');
                if (statsResp.ok) {
                    const stats = await statsResp.json();
                    renderIncidentStats(stats);
                }

                // Load incidents
                const incidentsResp = await fetch('/dashboard/incidents');
                if (incidentsResp.ok) {
                    const data = await incidentsResp.json();
                    renderIncidentsTable(data.incidents || []);
                }
            } catch (error) {
                console.error('Error loading incident dashboard:', error);
                document.getElementById('incidents-tbody').innerHTML =
                    '<tr><td colspan="7" style="text-align:center; padding:40px; color:#c33;">Error loading incidents</td></tr>';
            }
        }

        function renderIncidentStats(stats) {
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
            document.getElementById('incident-stats').innerHTML = statsHtml;
        }

        function renderIncidentsTable(incidents) {
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

            document.getElementById('incidents-tbody').innerHTML = rows;
        }

        // =======================
        // Evaluation Metrics
        // =======================

        async function loadEvaluationMetrics() {
            try {
                // Load stats
                const statsResp = await fetch('/evaluations/stats');
                if (statsResp.ok) {
                    const stats = await statsResp.json();
                    renderEvaluationStats(stats);
                }

                // Load evaluations
                const evaluationsResp = await fetch('/evaluations');
                if (evaluationsResp.ok) {
                    const data = await evaluationsResp.json();
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

            const rows = evaluations.map(eval => `
                <tr>
                    <td><strong>${eval.workflowId}</strong></td>
                    <td><span class="badge ${eval.summaryToxicityPassed ? 'passed' : 'failed'}">${eval.summaryToxicityPassed ? 'PASS' : 'FAIL'}</span></td>
                    <td><span class="badge ${eval.remediationToxicityPassed ? 'passed' : 'failed'}">${eval.remediationToxicityPassed ? 'PASS' : 'FAIL'}</span></td>
                    <td><span class="badge ${eval.evidenceHallucinationPassed ? 'passed' : 'failed'}">${eval.evidenceHallucinationPassed ? 'PASS' : 'FAIL'}</span></td>
                    <td><span class="badge ${eval.triageHallucinationPassed ? 'passed' : 'failed'}">${eval.triageHallucinationPassed ? 'PASS' : 'FAIL'}</span></td>
                    <td><span class="badge ${eval.summaryHallucinationPassed ? 'passed' : 'failed'}">${eval.summaryHallucinationPassed ? 'PASS' : 'FAIL'}</span></td>
                    <td>${formatDateTime(eval.evaluatedAt)}</td>
                </tr>
            `).join('');

            document.getElementById('evaluations-tbody').innerHTML = rows;
        }

        // =======================
        // Utility Functions
        // =======================

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

        // Cleanup on page unload
        window.addEventListener('beforeunload', () => {
            if (pollingInterval) {
                clearInterval(pollingInterval);
            }
        });
