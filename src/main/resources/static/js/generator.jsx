(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect, useRef } = React;
    const AuthApi = namespace.modules.api;
    const { SaveDiagramDialog, mapTypeToEnum } = namespace.modules.projects;

    function MermaidDiagram({ code }) {
    const containerRef = useRef(null);
    useEffect(() => {
        if (!containerRef.current || !code) return;
        const id = 'mermaid-' + Math.random().toString(36).slice(2, 10);
        mermaid.render(id, code)
            .then(({ svg }) => {
                if (containerRef.current) containerRef.current.innerHTML = svg;
            })
            .catch(() => {
                if (containerRef.current) {
                    containerRef.current.innerHTML =
                        '<pre style="color:#f87171;font-size:0.8rem;white-space:pre-wrap">' +
                        code.replace(/</g, '&lt;') + '</pre>';
                }
            });
    }, [code]);
    return <div ref={containerRef} style={{width:'100%'}} />;
}

function ResultMetadata({ result }) {
    if (!result) return null;
    const conf = result.confidenceScore ?? 100;
    const tier = conf >= 70 ? 'high' : conf >= 40 ? 'med' : 'low';
    const mode = result.generationMode || 'RULE_BASED';
    const model = result.modelUsed || 'Rule-Based';
    const badgeClass = mode === 'LLM' ? 'llm' : mode === 'TEMPLATE_FALLBACK' ? 'fallback' : 'rule';
    const badgeLabel = mode === 'LLM' ? '\u2736 AI (LLM)'
                     : mode === 'TEMPLATE_FALLBACK' ? '\u26A0 Fallback Template'
                     : '\u2699 Rule-Based';
    return (
        <div className="classification-info">
            <div className="class-item">
                <span className="class-label">Detected Type</span>
                <span className="class-value">{String(result.diagramType)}</span>
            </div>
            <div className="class-item">
                <span className="class-label">Confidence</span>
                <div className="class-confidence">
                    <div className="conf-bar">
                        <div className={`conf-fill ${tier}`} style={{width: conf + '%'}} />
                    </div>
                    <span className="class-value">{conf}%</span>
                </div>
            </div>
            <div className="class-item">
                <span className="class-label">Generation Mode</span>
                <span className={`class-mode-badge ${badgeClass}`}>{badgeLabel}</span>
            </div>
            <div className="class-item">
                <span className="class-label">Model</span>
                <span className="class-value" style={{color: '#64748b', fontSize: '0.8rem'}}>{model}</span>
            </div>
        </div>
    );
}

const DIAGRAM_TYPES = [
    { value: '', label: 'Auto-detect' },
    { value: 'CLASS', label: 'Class Diagram' },
    { value: 'SEQUENCE', label: 'Sequence Diagram' },
    { value: 'ER', label: 'ER Diagram' },
    { value: 'COMPONENT', label: 'Component Diagram' },
    { value: 'DEPLOYMENT', label: 'Deployment Diagram' },
    { value: 'USE_CASE', label: 'Use Case Diagram' },
    { value: 'OBJECT', label: 'Object Diagram' },
    { value: 'ACTIVITY', label: 'Activity Diagram' },
    { value: 'STATE', label: 'State Diagram' },
    { value: 'COLLABORATION', label: 'Collaboration Diagram' },
    { value: 'MICROSERVICES_ARCHITECTURE', label: 'Microservices Architecture' },
];

const DEMO_EXAMPLES = [
    {
        label: '— Demo Examples —',
        value: '',
        type: '',
        text: '',
    },
    {
        label: 'Sequence — User Login Flow',
        value: 'seq-login',
        type: 'SEQUENCE',
        text: 'A user submits their credentials to the login page. The auth service validates the credentials against the user database. If valid, the auth service generates a JWT token and returns it to the client. The client stores the token and uses it for subsequent API requests.',
    },
    {
        label: 'Sequence — Web Search',
        value: 'seq-web-search',
        type: 'SEQUENCE',
        text: 'User sends searchQuery() to Web Server. Web Server sends query() to SQL Server. SQL Server returns results() to Web Server. Web Server returns searchResults() to User.',
    },
    {
        label: 'Sequence — Internet Payment',
        value: 'seq-payment',
        type: 'SEQUENCE',
        text: 'User sends submitPayment() to Web Server. Web Server sends chargeRequest() to Transaction Server. Transaction Server sends authorise() to Bank. Bank confirms authorisation() to Transaction Server. Transaction Server returns receipt() to Web Server. Web Server returns confirmation() to User.',
    },
    {
        label: 'Sequence — ATM Withdraw',
        value: 'seq-atm',
        type: 'SEQUENCE',
        text: 'User sends insertCard() to ATM. ATM sends validateCard() to Bank. Bank returns cardValid() to ATM. User sends enterPin() to ATM. ATM sends verifyPin() to Bank. Bank confirms pinValid() to ATM. User sends requestCash() to ATM. If balance is sufficient, ATM sends dispenseCash() to Dispenser. Dispenser returns cashDispensed() to ATM. ATM returns receipt() to User. Otherwise ATM returns insufficientFunds() to User.',
    },
    {
        label: 'Sequence — Parallel Transaction',
        value: 'seq-par-transaction',
        type: 'SEQUENCE',
        text: 'User sends placeOrder() to Web Server. Web Server sends reserveStock() to Transaction Server. Web Server sends processPayment() to Transaction Server simultaneously. Transaction Server returns stockReserved() to Web Server at the same time. Transaction Server returns paymentProcessed() to Web Server simultaneously. Web Server returns orderConfirmed() to User.',
    },
    {
        label: 'Sequence — Soda Machine',
        value: 'seq-soda',
        type: 'SEQUENCE',
        text: 'User sends insertCoin() to Register. Register returns coinAccepted() to User. User sends selectSoda() to Register. Register sends checkStock() to Dispenser. If soda is available, Dispenser returns sodaAvailable() to Register. Register sends dispenseSoda() to Dispenser. Dispenser returns sodaDispensed() to Register. Register returns change() to User. Otherwise Dispenser returns outOfStock() to Register. Register returns refundCoin() to User.',
    },
    {
        label: 'Use Case — University Enrollment',
        value: 'uc-enrollment',
        type: 'USE_CASE',
        text: 'Actors include Student and Administrator.\nThe student can login, view grades, download materials, and enroll in courses.\nThe administrator can manage courses and approve enrollments.\nEnroll in courses must login first.\nDownload materials must login first.\nView grades might send notification.',
    },
    {
        label: 'ER — E-Commerce Store',
        value: 'er-ecommerce',
        type: 'ER',
        text: 'A Customer places many Orders. Each Order contains one or more OrderItems. Each OrderItem references a Product. A Product belongs to a Category. An Order is processed through a Payment and shipped to a ShippingAddress.',
    },
    {
        label: 'Class — Banking System',
        value: 'cls-banking',
        type: 'CLASS',
        text: 'A BankAccount has an accountNumber, balance, and owner. It supports deposit and withdraw operations. A SavingsAccount extends BankAccount and adds an interestRate. A CheckingAccount extends BankAccount and adds an overdraftLimit. A Customer owns multiple BankAccounts and has a name, email, and customerId.',
    },
    {
        label: 'Component — Microservices Architecture',
        value: 'cmp-microservices',
        type: 'COMPONENT',
        text: 'The API Gateway routes requests to the Auth Service, Order Service, and Product Service. The Auth Service connects to the User Database. The Order Service depends on the Inventory Service and the Payment Service. The Payment Service integrates with an external Payment Gateway. All services publish events to a Message Broker consumed by the Notification Service.',
    },
    {
        label: 'Deployment — Cloud Web Application',
        value: 'dep-cloud',
        type: 'DEPLOYMENT',
        text: 'The application runs on AWS. A CloudFront CDN sits in front of an Application Load Balancer. The load balancer distributes traffic to two EC2 instances running the Spring Boot application. The application instances connect to an RDS PostgreSQL database in a private subnet. An S3 bucket stores static assets. All components are deployed inside a VPC.',
    },
    {
        label: 'Object — Shopping Cart Snapshot',
        value: 'obj-cart',
        type: 'OBJECT',
        text: 'A snapshot of a shopping cart at checkout. The Cart instance belongs to Customer john_doe. The Cart contains two OrderItem instances. OrderItem1 references Product laptop with quantity 1 and price 999. OrderItem2 references Product mouse with quantity 2 and price 25. The Customer instance has address set to New York.',
    },
    {
        label: 'Activity — User Registration Flow',
        value: 'act-register',
        type: 'ACTIVITY',
        text: 'The user opens the registration form and fills in their name, email, and password. The system validates the input. If the email is already taken, the system shows an error and prompts the user to try again. If valid, the system creates the account, sends a verification email, and redirects the user to the dashboard.',
    },
    {
        label: 'Activity — Arriving at College',
        value: 'act-college',
        type: 'ACTIVITY',
        text: '1. Wake up\n2. Have breakfast\n3. Pack bag\n4. Leave home\n5. Travel to college\n6. Arrive at campus\n7. Attend morning lecture\n8. Take a break\n9. Attend afternoon lab\n10. Go home',
    },
    {
        label: 'Activity — Taxi or Bus Decision',
        value: 'act-taxi-bus',
        type: 'ACTIVITY',
        text: 'Leave the office. Check the weather. If it is raining then take a taxi otherwise take the bus. Arrive at destination.',
    },
    {
        label: 'Activity — Beverage Vending Machine',
        value: 'act-vending',
        type: 'ACTIVITY',
        text: 'Insert coin. Select beverage. Check if the selected item is available. If the item is available then dispense the beverage and return change otherwise return the coin and display out of stock message. Collect the beverage.',
    },
    {
        label: 'Activity — Consulting Firm Swimlane',
        value: 'act-consulting',
        type: 'ACTIVITY',
        text: 'Salesperson: Call client\nSalesperson: Send proposal\nConsultant: Review requirements\nConsultant: Prepare presentation\nTechnician: Install equipment\nTechnician: Run tests\nCustomer: Sign contract',
    },
    {
        label: 'Activity — Loop: Process Orders',
        value: 'act-loop',
        type: 'ACTIVITY',
        text: 'Receive order batch. For each order, validate the order details, calculate the total, and dispatch the shipment. Generate daily summary report.',
    },
    {
        label: 'State — Order Lifecycle',
        value: 'sta-order',
        type: 'STATE',
        text: 'An order starts in the Pending state. When the payment is received the order transitions to Confirmed. After the warehouse picks the items the order moves to Shipped. Once the carrier delivers the package the order becomes Delivered. If the customer returns the package the order transitions to Returned. At any point before shipping the order can be Cancelled.',
    },
    {
        label: 'State — Light Switch',
        value: 'sta-light',
        type: 'STATE',
        text: '[*] --> Off\nOff --> On : switch on\nOn --> Off : switch off',
    },
    {
        label: 'State — CD Player',
        value: 'sta-cd-player',
        type: 'STATE',
        text: '[*] --> Stopped\nStopped --> Playing : play\nPlaying --> Stopped : stop\nPlaying --> Paused : pause button\nPaused --> Playing : play\nPaused --> Stopped : stop\nStopped --> [*]\nstate Playing {\n  do / read CD\n}\nstate Paused {\n  exit / turn green LED on\n}',
    },
    {
        label: 'State — CD Player (Advanced)',
        value: 'sta-cd-player-advanced',
        type: 'STATE',
        text: '[*] --> Stopped\nStopped --> Playing : play button\nStopped --> TurnedOff : power off button\nStopped --> TurnedOff : after 30 min\nPlaying --> Paused : pause button\nPlaying --> Stopped : stop button\nPlaying --> TurnedOff : power off button\nPaused --> Playing : play button\nPaused --> Stopped : stop button\nPaused --> TurnedOff : power off button\nTurnedOff --> [*]\nstate Playing {\n  entry / turn blue LED on\n  do / read CD\n  exit / turn blue LED off\n}\nstate Paused {\n  entry / blink green LED\n  do / wait for input\n  exit / turn green LED on\n}\nstate Stopped {\n  entry / reset track position\n  do / idle\n}',
    },
    {
        label: 'State — Screen Saver (Timed)',
        value: 'sta-screensaver',
        type: 'STATE',
        text: '[*] --> Working\nWorking --> Screensaving : after 15 min\nScreensaving --> Working : keystroke\nWorking --> Stopped : power off\nStopped --> [*]',
    },
    {
        label: 'State — GUI Lifecycle',
        value: 'sta-gui',
        type: 'STATE',
        text: '[*] --> Initializing\nInitializing : do / booting up\nInitializing --> Working\nWorking --> ShuttingDown : shut down\nShuttingDown --> [*]',
    },
    {
        label: 'State — Screen Saving (Timed + Guarded)',
        value: 'sta-screensaving',
        type: 'STATE',
        text: '[*] --> Working\nWorking --> Screensaving : after 15 min unattended\nScreensaving --> Working : keystroke\nScreensaving --> [*] : shutdown',
    },
    {
        label: 'Component — Portal System',
        value: 'cmp-portal',
        type: 'COMPONENT',
        text: 'component "Index Page"\ncomponent "Menu Page"\ncomponent "Login"\ncomponent "News"\ncomponent "File Download"\n\n"Menu Page" --> "Index Page"\n"Login" --> "Index Page"\n"News" --> "Menu Page"\n"File Download" --> "Menu Page"',
    },
    {
        label: 'Component — Sales Web App (SSL + JDBC)',
        value: 'cmp-sales',
        type: 'COMPONENT',
        text: 'component "Web Browser"\ncomponent "Sales Software"\ndatabase "MySQL"\n\n"Web Browser" --> "Sales Software" : SSL\n"Sales Software" --> "MySQL" : JDBC',
    },
    {
        label: 'Component — Web App Stack',
        value: 'cmp-web-stack',
        type: 'COMPONENT',
        text: 'Web Browser --> Apache Server : HTTP\nApache Server --> Java Servlet : HTTP\nJava Servlet --> MySQL : JDBC\nJava Servlet --> Apache Server : SSL',
    },
    {
        label: 'Collaboration — Checkout Process',
        value: 'col-checkout',
        type: 'COLLABORATION',
        text: 'The Browser sends a place order request to the OrderController. The OrderController asks the InventoryService to reserve the items. The InventoryService responds with a reservation confirmation. The OrderController then calls the PaymentService to charge the customer. The PaymentService returns a transaction id. Finally the OrderController notifies the NotificationService to send an order confirmation email.',
    },
    {
        label: 'Microservices — E-Commerce Platform',
        value: 'ms-ecommerce',
        type: 'MICROSERVICES_ARCHITECTURE',
        text: 'The API Gateway routes all incoming traffic and forwards requests to the Auth Service, Product Service, Order Service, and Cart Service. The Auth Service manages authentication and connects to the User Database. The Product Service retrieves catalogue data from the Product Database. The Order Service depends on the Inventory Service and the Payment Service. The Payment Service integrates with an external Stripe Gateway. All services publish domain events to a Kafka message broker consumed by the Notification Service.',
    },
];

    function GeneratorView({ currentUser, projects, loadProjects, go, notify }) {
    const [text, setText] = useState('A user logs in through an auth service that queries a database');
    const [diagramType, setDiagramType] = useState('');
    const [selectedDemo, setSelectedDemo] = useState('');
    const [result, setResult] = useState(null);
    const [suggestion, setSuggestion] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);
    const [pdfFile, setPdfFile] = useState(null);
    const [pdfLoading, setPdfLoading] = useState(false);
    const [showFullPdfText, setShowFullPdfText] = useState(false);
    const [saveOpen, setSaveOpen] = useState(false);
    const [savedDiagram, setSavedDiagram] = useState(null);
    const pdfInputRef = useRef(null);

    async function handleGenerate(overrideType = null, forceGenerate = false) {
        setError(null);
        setResult(null);
        setSuggestion(null);
        setSavedDiagram(null);
        const resolvedType = overrideType || diagramType || null;
        if (!text || !text.trim()) {
            if (!resolvedType) {
                setError('Please enter a description.');
                return;
            }
        }
        const isManual = !!resolvedType;
        setLoading(true);
        try {
            const body = { text: text || '' };
            if (resolvedType) body.diagramType = resolvedType;
            if (forceGenerate) body.forceGenerate = true;
            const res = await fetch('/api/diagram/generate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            const envelope = await res.json();
            const data = envelope.data || envelope;
            if (!res.ok && res.status !== 422) throw new Error(data.message || envelope.message || 'HTTP ' + res.status);
            if (isManual) {
                setResult(data);
                return;
            }
            if (data.decision === 'SUGGEST') {
                setSuggestion({ message: data.message, diagramType: data.diagramType, confidenceScore: data.confidenceScore ?? 0 });
                return;
            }
            if (data.decision === 'REJECT') {
                setError(data.message || 'Please provide a more detailed description so the system can identify the diagram type.');
                return;
            }
            setResult(data);
        } catch (e) {
            setError(e.message);
        } finally {
            setLoading(false);
        }
    }

    function handleProceed() {
        if (suggestion?.diagramType) handleGenerate(mapTypeToEnum(suggestion.diagramType), true);
    }

    async function downloadDiagram(format) {
        try {
            const res = await fetch(`/api/diagram/${result.id}/${format}`);
            if (!res.ok) throw new Error(`Download failed (${format.toUpperCase()}): HTTP ${res.status}`);
            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `diagram-${result.id}.${format === 'drawio' ? 'drawio' : format}`;
            a.click();
            URL.revokeObjectURL(url);
        } catch (e) {
            setError(e.message);
        }
    }

    function handleKeyDown(e) {
        if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') handleGenerate();
    }

    function handleDemoSelect(e) {
        const val = e.target.value;
        setSelectedDemo(val);
        if (!val) return;
        const example = DEMO_EXAMPLES.find(ex => ex.value === val);
        if (!example) return;
        setText(example.text);
        setDiagramType(example.type);
        setResult(null);
        setSuggestion(null);
        setError(null);
        setSavedDiagram(null);
    }

    async function handlePdfUpload() {
        if (!pdfFile) return;
        setError(null);
        setResult(null);
        setSuggestion(null);
        setSavedDiagram(null);
        setPdfLoading(true);
        try {
            const formData = new FormData();
            formData.append('file', pdfFile);
            const res = await fetch('/api/diagrams/from-pdf', { method: 'POST', body: formData });
            const envelope = await res.json();
            const data = envelope.data || envelope;
            if (!res.ok) throw new Error(data.message || envelope.message || 'HTTP ' + res.status);
            if (data.id) data.diagramImageUrl = `/api/diagrams/${data.id}/png`;
            if (data.confidenceScore == null) data.confidenceScore = 100;
            if (!data.modelUsed) data.modelUsed = 'Rule-Based';
            if (!data.generationMode) data.generationMode = 'RULE_BASED';
            setResult(data);
        } catch (e) {
            setError(e.message);
        } finally {
            setPdfLoading(false);
        }
    }

    function canSave(result) {
        return currentUser && result && (result.plantUmlCode || result.id) && !(result.mermaidCode && !result.plantUmlCode);
    }

    return (
        <div className="app">
            <h1>AI Diagram Generator</h1>
            <p className="subtitle">Describe your system and get a UML diagram instantly</p>
            <div className="form">
                <div className="demo-bar">
                    <span className="demo-badge">Demo Examples</span>
                    <select className="demo-select" value={selectedDemo} onChange={handleDemoSelect}>
                        {DEMO_EXAMPLES.map(ex => <option key={ex.value} value={ex.value}>{ex.label}</option>)}
                    </select>
                </div>
                <div>
                    <label htmlFor="diagram-description-input">Description</label>
                    <textarea id="diagram-description-input" value={text} onChange={e => { setText(e.target.value); if (suggestion) setSuggestion(null); }} onKeyDown={handleKeyDown} placeholder="Describe your system, e.g. 'A user authenticates via an API that queries a database'" />
                </div>
                <div className="row">
                    <div>
                        <label htmlFor="diagram-type-select">Diagram Type</label>
                        <select id="diagram-type-select" value={diagramType} onChange={e => setDiagramType(e.target.value)}>
                            {DIAGRAM_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                        </select>
                        <div className="mode-indicator">
                            {diagramType ? <span>Mode: <span className="badge-manual">Manual ({DIAGRAM_TYPES.find(t => t.value === diagramType)?.label})</span></span> : <span>Mode: <span className="badge-auto">Auto-detect</span></span>}
                        </div>
                    </div>
                    <button onClick={() => handleGenerate()} disabled={loading}>
                        {loading && <span className="spinner" />}
                        {loading ? 'Generating...' : diagramType ? `Generate ${DIAGRAM_TYPES.find(t => t.value === diagramType)?.label ?? diagramType}` : 'Generate'}
                    </button>
                </div>
            </div>
            <div className="divider">or upload a PDF</div>
            <div className="pdf-section">
                <input ref={pdfInputRef} type="file" accept=".pdf" onChange={e => setPdfFile(e.target.files[0] || null)} />
                <button className="btn-choose-file" onClick={() => pdfInputRef.current.click()} disabled={pdfLoading}>Choose PDF</button>
                <span className="pdf-filename">{pdfFile ? pdfFile.name : 'No file chosen'}</span>
                <button className="btn-pdf" onClick={handlePdfUpload} disabled={pdfLoading || !pdfFile}>
                    {pdfLoading && <span className="spinner" />}
                    {pdfLoading ? 'Generating...' : 'Generate from PDF'}
                </button>
            </div>
            {error && <div className="error">{error}</div>}
            {suggestion && (
                <div className="suggestion">
                    <div className="suggestion-header">Classification Suggestion</div>
                    <div className="suggestion-meta">
                        <span className="suggestion-type">{suggestion.diagramType}</span>
                        <span className="suggestion-confidence">Confidence: {suggestion.confidenceScore}%</span>
                    </div>
                    {(() => {
                        const s = suggestion.confidenceScore;
                        const tier = s >= 70 ? 'high' : s >= 40 ? 'med' : 'low';
                        return <div className="suggestion-conf-bar"><div className="suggestion-conf-track"><div className={`suggestion-conf-fill ${tier}`} style={{width: s + '%'}} /></div><span className="suggestion-conf-label">{s}%</span></div>;
                    })()}
                    <p className="suggestion-message">{suggestion.message || <>Your description appears to describe a <strong>{suggestion.diagramType}</strong>. Do you want to proceed?</>}</p>
                    <div className="suggestion-actions">
                        <button className="btn-proceed" onClick={handleProceed} disabled={loading}>{loading ? 'Generating...' : 'Proceed'}</button>
                        <button className="btn-dismiss" onClick={() => setSuggestion(null)} disabled={loading}>Cancel</button>
                    </div>
                </div>
            )}
            {result && (
                <div className="result">
                    <div className="result-header">
                        <h2>Result</h2>
                        <span className="badge">{result.diagramType}</span>
                    </div>
                    <ResultMetadata result={result} />
                    {result.generationMode === 'TEMPLATE_FALLBACK' && <div className="fallback-notice"><span className="fallback-notice-icon">&#x26A0;&#xFE0F;</span><span>{result.message || 'Diagram generation failed. Showing a default template instead.'}</span></div>}
                    <div className="diagram-container">
                        {result.svgContent ? <div dangerouslySetInnerHTML={{ __html: result.svgContent }} />
                            : result.pngBase64 ? <img src={`data:image/png;base64,${result.pngBase64}`} alt="Generated diagram" />
                            : result.mermaidCode ? <MermaidDiagram code={result.mermaidCode} />
                            : result.diagramImageUrl ? <img src={result.diagramImageUrl} alt="Generated diagram" style={{maxWidth: '100%'}} />
                            : <p style={{color: '#64748b'}}>Diagram rendering unavailable - see PlantUML code below.</p>}
                    </div>
                    {result.id && (
                        <div className="download-actions">
                            <button className="btn-download-png" onClick={() => downloadDiagram('png')}>Download PNG</button>
                            <button className="btn-download-svg" onClick={() => downloadDiagram('svg')}>Download SVG</button>
                            <button className="btn-download-drawio" onClick={() => downloadDiagram('drawio')}>Download Draw.io XML</button>
                        </div>
                    )}
                    {currentUser && result.mermaidCode && !result.plantUmlCode && <div className="notice info save-panel">Workspace saving currently supports PlantUML only.</div>}
                    {canSave(result) && (
                        <div className="save-panel toolbar-actions">
                            <button className="btn-success" onClick={() => setSaveOpen(true)}>Save to Project</button>
                            {savedDiagram && (
                                <>
                                    <button className="btn-secondary" onClick={() => go('project', { projectId: savedDiagram.projectId })}>Open Project</button>
                                    <button className="btn-secondary" onClick={() => go('diagram', { diagramId: savedDiagram.id, projectId: savedDiagram.projectId })}>Open Diagram</button>
                                </>
                            )}
                        </div>
                    )}
                    {!currentUser && <div className="notice info save-panel">Log in to save generated PlantUML diagrams into projects.</div>}
                    {result.message && <p className="explanation">{result.message}</p>}
                    {result.extractedTextPreview && (() => {
                        const PREVIEW_LIMIT = 400;
                        const cleaned = cleanPdfText(result.extractedTextPreview);
                        const isTruncated = cleaned.length > PREVIEW_LIMIT;
                        const displayed = showFullPdfText || !isTruncated ? cleaned : cleaned.slice(0, PREVIEW_LIMIT).replace(/\s+\S*$/, '') + '...';
                        return <div className="pdf-preview"><div className="pdf-preview-label">Extracted from PDF:</div><div className="pdf-preview-text">{displayed}</div>{isTruncated && <button className="pdf-preview-toggle" onClick={() => setShowFullPdfText(v => !v)}>{showFullPdfText ? 'Show less' : 'Show full extracted text'}</button>}</div>;
                    })()}
                    {result.explanation && (
                        <details className="gen-explanation">
                            <summary>How this diagram was generated</summary>
                            <div className="gen-explanation-body">
                                {result.explanation.extractedEntities?.length > 0 && <div><div className="gen-section-label">Extracted entities</div><div className="entity-chips">{result.explanation.extractedEntities.map((e, i) => <span key={i} className="entity-chip">{e}</span>)}</div></div>}
                                {result.explanation.detectedRelationships?.length > 0 && <div><div className="gen-section-label">Detected relationships</div><div className="rel-list">{result.explanation.detectedRelationships.map((r, i) => <div key={i} className="rel-item"><span className="rel-node">{r.source}</span><span className="rel-arrow">&#x2192;</span><span className="rel-node">{r.target}</span>{r.type && <span className="rel-type-badge">{r.type}</span>}</div>)}</div></div>}
                                {result.explanation.typeReasoning && <div><div className="gen-section-label">Diagram type reasoning</div><p className="reasoning-text">{result.explanation.typeReasoning}</p></div>}
                            </div>
                        </details>
                    )}
                    {result.plantUmlCode && (
                        <div className="code-block">
                            {!result.svgContent && !result.pngBase64 && !result.diagramImageUrl && <div className="render-error"><strong>Rendering failed.</strong> The diagram could not be rendered as an image. The raw PlantUML code is shown below.</div>}
                            <div className="code-block-label">PlantUML code</div>
                            <pre>{result.plantUmlCode}</pre>
                        </div>
                    )}
                </div>
            )}
            {saveOpen && (
                <SaveDiagramDialog
                    result={result}
                    originalPrompt={text}
                    fallbackType={diagramType}
                    projects={projects}
                    loadProjects={loadProjects}
                    go={go}
                    notify={notify}
                    onClose={() => setSaveOpen(false)}
                    onSaved={diagram => {
                        setSavedDiagram(diagram);
                        setSaveOpen(false);
                        notify('success', result.id ? 'Diagram attached to project.' : 'Diagram saved to project.');
                    }}
                />
            )}
        </div>
    );
}

    namespace.modules.generator = {
        GeneratorView
    };
})();
