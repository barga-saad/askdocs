import { ChatPanel } from './ChatPanel';
import { DocumentsPanel } from './DocumentsPanel';

export function App() {
  return (
    <>
      <header className="topbar">
        <strong>AskDocs</strong>
        <span className="muted small">Ask questions about your documents</span>
      </header>
      <main className="layout">
        <DocumentsPanel />
        <ChatPanel />
      </main>
    </>
  );
}
