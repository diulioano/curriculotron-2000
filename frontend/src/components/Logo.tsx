/** Robô rabiscado + "Curriculotron 2000" (design/). */
export function Logo({ size = 28 }: { size?: number }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 12 }}>
      <svg
        width={size * 1.64}
        height={size * 1.64}
        viewBox="0 0 46 46"
        fill="none"
        stroke="currentColor"
        strokeWidth={2.4}
        strokeLinejoin="miter"
        strokeLinecap="square"
        aria-hidden="true"
      >
        <path d="M8 14 L38 12 L40 38 L6 40 Z" />
        <path d="M14 22 L19 21 L18 27 Z" />
        <path d="M27 21 L33 20 L32 26 Z" />
        <path d="M15 33 L20 31 L25 34 L31 31" />
        <path d="M23 12 L22 4" />
        <path d="M19 4 L26 3" />
      </svg>
      <span
        style={{
          fontFamily: 'var(--font-marker)',
          fontSize: size,
          letterSpacing: 0.5,
          lineHeight: 1,
          whiteSpace: 'nowrap',
        }}
      >
        Curriculotron{' '}
        <span
          style={{
            fontFamily: 'var(--font-mono)',
            fontWeight: 700,
            fontSize: Math.round(size * 0.54),
            marginLeft: 6,
            verticalAlign: 'super',
          }}
        >
          2000
        </span>
      </span>
    </span>
  );
}

/** Filtro SVG que entorta os traços (.sk, .bt-pri). Renderizar uma vez por página. */
export function RoughFilter() {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden="true" focusable="false">
      <filter id="rough">
        <feTurbulence type="fractalNoise" baseFrequency="0.03" numOctaves={2} seed={4} result="n" />
        <feDisplacementMap in="SourceGraphic" in2="n" scale={5} />
      </filter>
    </svg>
  );
}
