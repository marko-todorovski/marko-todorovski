(function () {
    const namespace = window.AiDiagramApp = window.AiDiagramApp || {};
    namespace.modules = namespace.modules || {};
    const { useState, useEffect, useRef } = React;
    const AuthApi = namespace.modules.api;
    const { Modal } = namespace.modules.shared;

    function AiAssistantPanel({ diagram, diagramId, editorSource, editorPrompt, setEditorSource, setEditorPrompt, renderPreview, saveAiVersion, notify }) {
        const [mode, setMode] = useState(null);
        const [loading, setLoading] = useState(false);
        const [message, setMessage] = useState('');
        const [explanation, setExplanation] = useState(null);
        const [suggestions, setSuggestions] = useState(null);
        const [instruction, setInstruction] = useState('');
        const [proposal, setProposal] = useState(null);
        const [proposalPreviewUrl, setProposalPreviewUrl] = useState(null);
        const [proposalPreviewMessage, setProposalPreviewMessage] = useState('');
        const proposalPreviewRef = useRef(null);

        const plantUml = diagram?.sourceFormat === 'PLANTUML';

        useEffect(() => () => revokeProposalPreview(), []);

        function revokeProposalPreview() {
            if (proposalPreviewRef.current) {
                URL.revokeObjectURL(proposalPreviewRef.current);
                proposalPreviewRef.current = null;
            }
        }

        function openMode(nextMode) {
            setMode(nextMode);
            setMessage('');
            if (nextMode !== 'modify') setProposal(null);
        }

        function close() {
            setMode(null);
            setMessage('');
            setProposal(null);
            revokeProposalPreview();
            setProposalPreviewUrl(null);
            setProposalPreviewMessage('');
        }

        async function explain() {
            setLoading(true);
            setMessage('');
            try {
                setExplanation(await AuthApi.explainDiagram(diagramId, { sourceCode: editorSource }));
                openMode('explain');
            } catch (e) {
                setMessage(e.message || 'Explanation could not be generated.');
            } finally {
                setLoading(false);
            }
        }

        async function getSuggestions(focus = 'general') {
            setLoading(true);
            setMessage('');
            try {
                setSuggestions(await AuthApi.suggestDiagramImprovements(diagramId, {
                    sourceCode: editorSource,
                    diagramType: diagram.diagramType,
                    focus
                }));
                openMode('suggestions');
            } catch (e) {
                setMessage(e.message || 'Suggestions could not be generated.');
            } finally {
                setLoading(false);
            }
        }

        async function modify() {
            if (!instruction.trim()) {
                setMessage('Describe the change you want AI to make.');
                return;
            }
            setLoading(true);
            setMessage('');
            revokeProposalPreview();
            setProposalPreviewUrl(null);
            setProposalPreviewMessage('Generating proposal...');
            try {
                const nextProposal = await AuthApi.modifyDiagramWithAi(diagramId, {
                    instruction,
                    sourceCode: editorSource,
                    diagramType: diagram.diagramType
                });
                setProposal(nextProposal);
                await renderProposalPreview(nextProposal.sourceCode);
            } catch (e) {
                setProposal(null);
                setProposalPreviewMessage(e.message || 'AI proposal could not be generated.');
            } finally {
                setLoading(false);
            }
        }

        async function renderProposalPreview(sourceCode) {
            try {
                const blob = await AuthApi.previewDiagram(sourceCode);
                const url = URL.createObjectURL(blob);
                revokeProposalPreview();
                proposalPreviewRef.current = url;
                setProposalPreviewUrl(url);
                setProposalPreviewMessage('Proposal preview rendered.');
            } catch (e) {
                setProposalPreviewMessage(e.message || 'Proposal preview could not be rendered.');
            }
        }

        function applyProposal() {
            if (!proposal) return;
            setEditorSource(proposal.sourceCode);
            setEditorPrompt(instruction ? `AI instruction: ${instruction}` : editorPrompt);
            renderPreview(proposal.sourceCode);
            notify('info', 'AI proposal loaded into editor. Save to create a version.');
        }

        async function saveProposal() {
            if (!proposal) return;
            await saveAiVersion(proposal, instruction);
            close();
        }

        function useSuggestion(suggestion) {
            setInstruction(`${suggestion.title}: ${suggestion.description}`);
            setMode('modify');
        }

        if (!plantUml) {
            return <section className="panel stack"><h2>AI Assistant</h2><p className="muted">AI editing is available for saved PlantUML diagrams only.</p></section>;
        }

        return (
            <section className="panel stack ai-assistant-panel">
                <div className="toolbar" style={{marginBottom: 0}}>
                    <h2>AI Assistant</h2>
                    <span className="badge">Proposal only</span>
                </div>
                <div className="toolbar-actions">
                    <button type="button" onClick={explain} disabled={loading || !editorSource.trim()}>{loading ? 'Working...' : 'Explain Diagram'}</button>
                    <button type="button" onClick={() => getSuggestions('general')} disabled={loading || !editorSource.trim()}>Suggest Improvements</button>
                    <button type="button" onClick={() => openMode('modify')} disabled={loading || !editorSource.trim()}>Modify with AI</button>
                </div>
                <div className="editor-status" aria-live="polite">{message}</div>

                {mode && (
                    <Modal title={modalTitle(mode)} onClose={close}>
                        <div className="stack ai-modal-body">
                            {mode === 'explain' && <ExplanationView explanation={explanation} />}
                            {mode === 'suggestions' && <SuggestionsView suggestions={suggestions} onUseSuggestion={useSuggestion} />}
                            {mode === 'modify' && (
                                <ModifyView
                                    instruction={instruction}
                                    setInstruction={setInstruction}
                                    loading={loading}
                                    modify={modify}
                                    proposal={proposal}
                                    currentSource={editorSource}
                                    proposalPreviewUrl={proposalPreviewUrl}
                                    proposalPreviewMessage={proposalPreviewMessage}
                                    applyProposal={applyProposal}
                                    saveProposal={saveProposal}
                                    discardProposal={() => setProposal(null)}
                                />
                            )}
                        </div>
                    </Modal>
                )}
            </section>
        );
    }

    function modalTitle(mode) {
        if (mode === 'explain') return 'Explain diagram';
        if (mode === 'suggestions') return 'Suggested improvements';
        return 'Modify with AI';
    }

    function ExplanationView({ explanation }) {
        if (!explanation) return <p className="muted">No explanation yet.</p>;
        return (
            <div className="stack">
                <p className="muted">{explanation.summary || explanation.explanation || 'No summary returned.'}</p>
                <AiList title="Elements" items={(explanation.elements || []).map(item => `${item.name || 'Element'} (${item.type || 'unknown'}): ${item.description || ''}`)} />
                <AiList title="Relationships" items={(explanation.relationships || []).map(item => `${item.from || '?'} -> ${item.to || '?'}: ${item.description || ''}`)} />
                {explanation.flow && <div><span className="detail-label">Flow</span><p className="muted">{explanation.flow}</p></div>}
                <AiList title="Risks" items={explanation.risks || []} />
            </div>
        );
    }

    function SuggestionsView({ suggestions, onUseSuggestion }) {
        const items = suggestions?.suggestions || [];
        return (
            <div className="stack">
                <p className="muted">{suggestions?.summary || 'Review these suggested improvements.'}</p>
                {items.length === 0 ? <p className="muted">No suggestions returned.</p> : items.map((suggestion, index) => (
                    <article className="history-item" key={`${suggestion.title}-${index}`}>
                        <div className="history-topline">
                            <span className="history-version">{suggestion.title || 'Suggestion'}</span>
                            <span className="badge">{suggestion.priority || 'MEDIUM'}</span>
                        </div>
                        <p className="muted">{suggestion.description}</p>
                        <button type="button" onClick={() => onUseSuggestion(suggestion)}>Use as Modify Instruction</button>
                    </article>
                ))}
            </div>
        );
    }

    function ModifyView({ instruction, setInstruction, loading, modify, proposal, currentSource, proposalPreviewUrl, proposalPreviewMessage, applyProposal, saveProposal, discardProposal }) {
        return (
            <div className="stack">
                <div>
                    <label htmlFor="ai-modify-instruction">Instruction</label>
                    <textarea
                        id="ai-modify-instruction"
                        className="prompt-textarea"
                        value={instruction}
                        onChange={e => setInstruction(e.target.value)}
                        placeholder="Add an authentication service between the user and API."
                    />
                </div>
                <div className="toolbar-actions">
                    <button type="button" onClick={modify} disabled={loading || !instruction.trim()}>{loading ? 'Generating...' : 'Generate Proposal'}</button>
                </div>
                <div className="editor-status" aria-live="polite">{proposalPreviewMessage}</div>
                {proposal && (
                    <div className="stack">
                        <p className="muted">{proposal.summary}</p>
                        <div className="proposal-preview">
                            {proposalPreviewUrl ? <img src={proposalPreviewUrl} alt="AI proposal preview" /> : <div className="preview-empty">Proposal preview will appear here.</div>}
                        </div>
                        <SourceComparison currentSource={currentSource} proposedSource={proposal.sourceCode} />
                        <div className="toolbar-actions">
                            <button type="button" onClick={applyProposal}>Apply to Editor</button>
                            <button type="button" className="btn-success" onClick={saveProposal}>Save as New Version</button>
                            <button type="button" className="btn-secondary" onClick={discardProposal}>Discard</button>
                        </div>
                    </div>
                )}
            </div>
        );
    }

    function SourceComparison({ currentSource, proposedSource }) {
        return (
            <div className="ai-comparison">
                <div>
                    <span className="detail-label">Current source</span>
                    <pre className="version-code ai-code-block">{currentSource}</pre>
                </div>
                <div>
                    <span className="detail-label">AI proposal source</span>
                    <pre className="version-code ai-code-block">{proposedSource}</pre>
                </div>
            </div>
        );
    }

    function AiList({ title, items }) {
        if (!items || items.length === 0) return null;
        return (
            <div>
                <span className="detail-label">{title}</span>
                <ul className="ai-list">
                    {items.map((item, index) => <li key={`${title}-${index}`}>{item}</li>)}
                </ul>
            </div>
        );
    }

    namespace.modules.aiAssistant = {
        AiAssistantPanel
    };
})();
