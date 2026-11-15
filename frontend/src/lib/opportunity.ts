import type { Opportunity } from '../api/panel';

const UNITS = {
  monthly: '',
  hourly: ' / hora',
  yearly: ' / ano',
  unknown: ' · período a confirmar',
};
const money = (n: number) =>
  n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });

/** "R$ 9.500 – 12.000" (mensal, como no design), "R$ 50 / hora", ou null sem salário declarado. */
export function salaryText(item: Opportunity): string | null {
  const salary = item.salary;
  if (!salary) return null;
  const amounts = [salary.min, salary.max].filter((n): n is number => n !== null);
  if (!amounts.length) return null;
  const text = amounts.map((n, i) => (i === 0 ? money(n) : n.toLocaleString('pt-BR'))).join(' – ');
  return `${text}${UNITS[salary.unit]}`;
}

/**
 * Cor por empresa (design/, "Padrões do rabisco"): só em traço, hachura e carimbo — o texto fica
 * sempre em tinta escura. Empresa sem marca conhecida usa grafite. Cores a conferir com o manual
 * de marca de cada empresa.
 */
const BRANDS: [RegExp, string][] = [
  [/^ita[uú]/i, '#EC7000'],
  [/^sap\b/i, '#0070F2'],
  [/^picpay/i, '#21C25E'],
  [/^sicredi/i, '#2F7D12'],
];
export const GRAPHITE = '#6B6B73';
export function companyColor(name: string) {
  const key = name.trim();
  return BRANDS.find(([pattern]) => pattern.test(key))?.[1] ?? GRAPHITE;
}

/** "It" para Itaú, "S" para SAP: primeira palavra; siglas em maiúsculas ficam com uma letra. */
export function companyInitials(name: string) {
  const word = name.trim().split(/\s+/)[0] || '?';
  return word === word.toUpperCase() ? word.slice(0, 1) : word.slice(0, 2);
}
