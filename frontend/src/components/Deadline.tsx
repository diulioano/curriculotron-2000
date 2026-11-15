import { useEffect, useState } from 'react';

export function Deadline({
  at,
  precision = 'exact',
}: {
  at: string | null | undefined;
  precision?: string;
}) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  if (!at || precision === 'ambiguous') return <span className="meta">Prazo a confirmar</span>;
  const date = new Date(at.length === 10 ? `${at}T23:59:59-03:00` : at);
  const hours = (date.getTime() - now) / 3_600_000;
  const urgency = hours < 0 ? 'overdue' : hours <= 48 ? 'urgent' : hours <= 168 ? 'soon' : '';
  const label = hours < 0 ? 'Prazo vencido' : hours <= 48 ? 'Urgente' : 'Prazo';
  return (
    <span className={`deadline ${urgency}`}>
      <span aria-hidden="true">◷ </span>
      {label}:{' '}
      <time dateTime={at}>
        {date.toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' })}
        {precision === 'exact' && at.length > 10
          ? ` às ${date.toLocaleTimeString('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit' })}`
          : ''}
      </time>
    </span>
  );
}
