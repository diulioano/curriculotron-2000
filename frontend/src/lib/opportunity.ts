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

/** Cor decorativa; textos e foco mantêm a tinta escura do tema. */
const BRANDS = new Map<string, string>([
  ['itaú', '#EC7000'],
  ['itau', '#EC7000'],
  ['sap', '#0070F2'],
  ['picpay', '#21C25E'],
  ['sicredi', '#2F7D12'],
  ['bv', '#005CA9'], // Aproximação visual; não é um hex oficial confirmado.
  ['cwi', '#FF8A00'],
  ['db', '#F70773'],
  ['ci&t', '#FA5A50'],
]);
// Ordem e hash são estáveis entre sessões; colisões são permitidas.
const COMPANY_PALETTE = ['#475569', '#7C3AED', '#0F766E', '#B45309', '#BE185D', '#0369A1'];
export const GRAPHITE = '#6B6B73';
export function companyColor(name: string): string {
  const key = name.trim().toLowerCase();
  if (!key) return GRAPHITE;
  const brand = BRANDS.get(key);
  if (brand) return brand;
  let hash = 0;
  for (let index = 0; index < key.length; index++) {
    hash = (31 * hash + key.charCodeAt(index)) >>> 0;
  }
  return COMPANY_PALETTE[hash % COMPANY_PALETTE.length];
}
/** "It" para Itaú, "S" para SAP: primeira palavra; siglas em maiúsculas ficam com uma letra. */
export function companyInitials(name: string) {
  const word = name.trim().split(/\s+/)[0] || '?';
  return word === word.toUpperCase() ? word.slice(0, 1) : word.slice(0, 2);
}
