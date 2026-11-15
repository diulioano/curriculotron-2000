/**
 * Estado de integração/rotina que não depende de cor (NFR-005): sempre ícone + rótulo em texto.
 * A cor só reforça.
 */

export type IntegrationState = 'ok' | 'degraded' | 'disconnected' | 'paused';

const META: Record<IntegrationState, { icon: string; label: string; color: string }> = {
  ok: { icon: '✓', label: 'funcionando', color: 'var(--ok)' },
  degraded: { icon: '!', label: 'com falhas', color: 'var(--warn)' },
  disconnected: { icon: '✕', label: 'desconectado', color: 'var(--danger)' },
  paused: { icon: 'Ⅱ', label: 'pausado', color: 'var(--ink-muted)' },
};

interface Props {
  state: IntegrationState;
  /** Substitui o rótulo padrão (ex.: "limite do provedor"). */
  label?: string;
}

export function StatusBadge({ state, label }: Props) {
  const meta = META[state];
  return (
    <span
      className="mono"
      data-state={state}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        fontSize: 13,
        fontWeight: 700,
        color: meta.color,
        whiteSpace: 'nowrap',
      }}
    >
      <span
        aria-hidden="true"
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 20,
          height: 20,
          border: `2px solid ${meta.color}`,
          fontSize: 12,
          lineHeight: 1,
        }}
      >
        {meta.icon}
      </span>
      <span>{label ?? meta.label}</span>
    </span>
  );
}
