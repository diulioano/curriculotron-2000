import { companyColor, GRAPHITE } from '../../src/lib/opportunity';

describe('identidade de cor por empresa', () => {
  it.each([
    ['Itaú', '#EC7000'],
    ['itau', '#EC7000'],
    ['SAP', '#0070F2'],
    ['PicPay', '#21C25E'],
    ['Sicredi', '#2F7D12'],
    ['BV', '#005CA9'],
    ['CWI', '#FF8A00'],
    ['DB', '#F70773'],
    ['CI&T', '#FA5A50'],
  ])('%s conserva sua identidade de marca', (name, color) => {
    expect(companyColor(name)).toBe(color);
  });

  it.each(['', '   ', '\t\n'])('reserva grafite para empresa ausente: %j', (name) => {
    expect(companyColor(name)).toBe(GRAPHITE);
  });

  it('retorna grafite neutro para qualquer empresa fora das 8 marcas conferidas', () => {
    expect(companyColor('Empresa Nova')).toBe(GRAPHITE);
    expect(companyColor('SAP Outra Empresa')).toBe(GRAPHITE);
    expect(companyColor('constructor')).toBe(GRAPHITE);
    expect(companyColor('__proto__')).toBe(GRAPHITE);
  });

  it.each(['SAP', 'CI&T', 'Empresa Nova', '🧩 Tecnologia'])(
    'normaliza apenas caixa e bordas: %s',
    (name) => {
      expect(companyColor(`  ${name.toUpperCase()}  `)).toBe(companyColor(name.toLowerCase()));
    },
  );

  it('mantém a identidade independente de ordem, subconjunto e chamadas anteriores', () => {
    const names = ['SAP', 'Empresa Nova', 'BV', '🧩 Tecnologia'];
    const original = new Map(names.map((name) => [name, companyColor(name)]));
    for (const name of names.toReversed().slice(1)) {
      expect(companyColor(name)).toBe(original.get(name));
    }
    expect(companyColor('🧩 Tecnologia')).toBe(GRAPHITE);
    expect(companyColor('Empresa Nova')).toBe(GRAPHITE);
  });

  it.each([
    'SAP Brasil',
    'DB Tecnologia',
    'CI & T',
    'PicPay Outra Empresa',
    'Sicredi Outra',
    'Itau Outra',
  ])('não atribui marca a nome apenas parecido e retorna grafite: %s', (name) => {
    expect(companyColor(name)).toBe(GRAPHITE);
  });

  it('nome longo fora das marcas conferidas também recebe grafite neutro', () => {
    const name = 'Tecnologia 🧩 '.repeat(1000);
    expect(companyColor(name)).toBe(GRAPHITE);
  });
});
