export const ZONE = 'America/Sao_Paulo';

/** "hoje 07:40", "ontem 19:05" ou "10/11 09:00", no fuso de São Paulo. */
export function formatAsOf(at: string, now: Date = new Date()): string {
  const date = new Date(at);
  const dayKey = (d: Date) => d.toLocaleDateString('en-CA', { timeZone: ZONE });
  const time = date.toLocaleTimeString('pt-BR', {
    timeZone: ZONE,
    hour: '2-digit',
    minute: '2-digit',
  });
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  if (dayKey(date) === dayKey(now)) return `hoje ${time}`;
  if (dayKey(date) === dayKey(yesterday)) return `ontem ${time}`;
  const day = date.toLocaleDateString('pt-BR', {
    timeZone: ZONE,
    day: '2-digit',
    month: '2-digit',
  });
  return `${day} ${time}`;
}
