import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from './api';
import type { AskResponse } from './api';
import { modeLabel } from './format';

interface Exchange {
  id: number;
  question: string;
  response?: AskResponse;
  error?: string;
}

function Answer({ response }: { response: AskResponse }) {
  return (
    <div className="answer">
      <span className={`badge mode-${response.mode.toLowerCase()}`}>{modeLabel(response.mode)}</span>
      <p className="prewrap">{response.answer}</p>
      {response.sources.length > 0 && (
        <details>
          <summary className="small">Sources ({response.sources.length})</summary>
          <ol className="sources">
            {response.sources.map((source) => (
              <li key={source.number}>
                <div className="small">
                  <strong>[{source.number}] {source.filename}</strong>
                  <span className="muted"> · match {Math.round(source.score * 100)}%</span>
                </div>
                <p className="muted small prewrap">{source.excerpt}</p>
              </li>
            ))}
          </ol>
        </details>
      )}
    </div>
  );
}

export function ChatPanel() {
  const [question, setQuestion] = useState('');
  const [exchanges, setExchanges] = useState<Exchange[]>([]);
  const nextId = useRef(1);
  const bottom = useRef<HTMLDivElement>(null);

  const ask = useMutation({
    mutationFn: ({ text }: { id: number; text: string }) => api.ask(text),
    onSuccess: (response, { id }) => update(id, { response }),
    onError: (error, { id }) => update(id, { error: error.message }),
  });

  function update(id: number, changes: Partial<Exchange>) {
    setExchanges((current) => current.map((item) => (item.id === id ? { ...item, ...changes } : item)));
  }

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: 'nearest' });
  }, [exchanges]);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const text = question.trim();
    if (!text || ask.isPending) {
      return;
    }
    const id = nextId.current++;
    setExchanges((current) => [...current, { id, question: text }]);
    setQuestion('');
    ask.mutate({ id, text });
  }

  return (
    <section className="panel chat" aria-label="Questions">
      <div className="messages" aria-live="polite">
        {exchanges.length === 0 && (
          <div className="empty">
            <h2>Ask anything about your documents</h2>
            <p className="muted">Answers come only from the files you upload, with their sources.</p>
          </div>
        )}
        {exchanges.map((exchange) => (
          <article key={exchange.id}>
            <p className="question prewrap">{exchange.question}</p>
            {exchange.response && <Answer response={exchange.response} />}
            {exchange.error && <p className="error" role="alert">{exchange.error}</p>}
            {!exchange.response && !exchange.error && <p className="muted">Searching…</p>}
          </article>
        ))}
        <div ref={bottom} />
      </div>

      <form className="ask-form" onSubmit={handleSubmit}>
        <input
          aria-label="Your question"
          placeholder="Ask a question"
          maxLength={1000}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
        />
        <button className="primary" disabled={ask.isPending || !question.trim()}>Ask</button>
      </form>
    </section>
  );
}
