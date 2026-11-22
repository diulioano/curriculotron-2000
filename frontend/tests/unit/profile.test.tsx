import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../src/app/App';
import { createQueryClient } from '../../src/app/queryClient';
import { parsePreparation, parseProfilePreview, parseProfileView } from '../../src/api/profile';
import { parseFloor, provenanceFor, validate, toForm } from '../../src/lib/profile-form';

type Handler = (init: RequestInit | undefined) => Response | Promise<Response>;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const assessment = (criteriaVersion: number, state = 'ready') => ({
  criteriaVersion,
  state,
  stale: false,
  assessedCount: 3,
  remainingCount: 0,
  startedAt: null,
  finishedAt: null,
  errorCode: null,
});

const savedProfile = (overrides: Record<string, unknown> = {}) => ({
  version: 1,
  role: 'Desenvolvedor Backend Java',
  primaryTechnology: 'Java',
  secondaryTechnologies: ['Spring Boot'],
  flexibleTechnologies: ['Kotlin'],
  currentSeniority: 'pleno',
  targetLevels: ['pleno', 'senior'],
  priorities: ['SAP', 'CWI'],
  juniorFloorCltMonthlyBrl: 8000,
  juniorFloorPjMonthlyBrl: null,
  origin: 'markdown',
  updatedAt: '2026-11-21T12:00:00Z',
  ...overrides,
});

/** Roteia fetch por "MÉTODO caminho"; registra corpos enviados. */
function stubApi(routes: Record<string, Handler>) {
  const calls: { key: string; body: unknown }[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const key = `${init?.method ?? 'GET'} ${String(input)}`;
      calls.push({
        key,
        body:
          init?.body instanceof FormData
            ? init.body
            : init?.body
              ? JSON.parse(String(init.body))
              : undefined,
      });
      if (key === 'GET /api/session') {
        return json(200, { authenticated: true, setupRequired: false, csrfToken: 'csrf' });
      }
      const handler = routes[key];
      return handler ? handler(init) : json(404, { error: { code: 'not_found', message: 'x' } });
    }),
  );
  return calls;
}

function openAt(path: string) {
  window.history.replaceState({}, '', path);
  render(<App queryClient={createQueryClient()} />);
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.history.replaceState({}, '', '/');
});

describe('perfil por importação Markdown', () => {
  it('mostra as empresas já escolhidas no primeiro perfil sem atribuir origem documental na importação', async () => {
    let profile: ReturnType<typeof savedProfile> | null = null;
    const calls = stubApi({
      'GET /api/profile': () =>
        json(200, { profile, assessment: assessment(1), priorityCompanies: ['CI&T', 'SAP'] }),
      'POST /api/profile/import/preview': () =>
        json(200, {
          draft: {
            role: 'Desenvolvedor Backend Java',
            primaryTechnology: 'Java',
            secondaryTechnologies: ['Spring Boot'],
            flexibleTechnologies: [],
            currentSeniority: 'pleno',
            targetLevels: ['pleno'],
            priorities: [],
            juniorFloorCltMonthlyBrl: null,
            juniorFloorPjMonthlyBrl: null,
          },
          warnings: [],
          provenance: {
            role: { source: 'imported', line: 1, excerpt: 'Cargo: Desenvolvedor Backend Java' },
            primaryTechnology: {
              source: 'imported',
              line: 2,
              excerpt: 'Tecnologia principal: Java',
            },
            secondaryTechnologies: {
              source: 'imported',
              line: 3,
              excerpt: 'Secundárias: Spring Boot',
            },
            currentSeniority: { source: 'imported', line: 4, excerpt: 'Senioridade: Pleno' },
            targetLevels: { source: 'imported', line: 5, excerpt: 'Níveis alvo: Pleno' },
          },
        }),
      'PUT /api/profile': (init) => {
        const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
        profile = savedProfile({ ...body, version: 1 });
        return json(200, { profile, assessment: assessment(2, 'pending') });
      },
    });
    openAt('/perfil');
    await userEvent.type(
      await screen.findByLabelText('Ou cole o texto do currículo'),
      '- Cargo: Dev',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Ler e revisar' }));

    const form = await screen.findByRole('form', { name: 'Revisão do perfil' });
    const prioritiesField = within(form)
      .getByLabelText('Empresas prioritárias, em ordem (uma por linha)')
      .closest<HTMLElement>('.field')!;
    expect(
      within(prioritiesField).getByLabelText('Empresas prioritárias, em ordem (uma por linha)'),
    ).toHaveValue('CI&T\nSAP');
    expect(within(prioritiesField).getByText('Informado por você')).toBeVisible();
    expect(within(prioritiesField).queryByText('Lido do currículo')).not.toBeInTheDocument();
    expect(within(prioritiesField).queryByText('Corrigido por você')).not.toBeInTheDocument();

    await userEvent.type(
      within(prioritiesField).getByLabelText('Empresas prioritárias, em ordem (uma por linha)'),
      '{enter}BV',
    );
    expect(within(prioritiesField).getByText('Informado por você')).toBeVisible();
    expect(within(prioritiesField).queryByText('Corrigido por você')).not.toBeInTheDocument();

    // Desmarcar e remarcar um nível não deve marcar targetLevels como "Corrigido por você".
    const plenoCheckbox = within(form).getByRole('checkbox', { name: 'Pleno' });
    await userEvent.click(plenoCheckbox);
    await userEvent.click(plenoCheckbox);
    const levelsField = within(form).getByRole('group', { name: 'Níveis desejados' });
    expect(within(levelsField).getByText('Lido do currículo')).toBeVisible();
    expect(within(levelsField).queryByText('Corrigido por você')).not.toBeInTheDocument();

    await userEvent.click(within(form).getByRole('button', { name: 'Salvar perfil' }));
    expect(await screen.findByText('✓ Perfil salvo (v1).')).toBeVisible();
    const put = calls.find((c) => c.key === 'PUT /api/profile')?.body as Record<string, unknown>;
    expect(put.priorities).toEqual(['CI&T', 'SAP', 'BV']);
    expect((put.provenance as Record<string, unknown>).priorities).toEqual({
      source: 'declared',
      line: null,
      excerpt: null,
    });
    expect((put.provenance as Record<string, unknown>).targetLevels).toEqual({
      source: 'imported',
      line: 5,
      excerpt: 'Níveis alvo: Pleno',
    });
  });

  it('mostra as empresas já escolhidas para a pessoa revisar no primeiro perfil manual', async () => {
    stubApi({
      'GET /api/profile': () =>
        json(200, { profile: null, assessment: assessment(1), priorityCompanies: ['CI&T', 'SAP'] }),
    });
    openAt('/perfil');
    await userEvent.click(await screen.findByRole('button', { name: 'Preencher manualmente' }));
    expect(screen.getByLabelText('Empresas prioritárias, em ordem (uma por linha)')).toHaveValue(
      'CI&T\nSAP',
    );
  });

  it('envia arquivo PDF via multipart para pré-visualização e abre o rascunho preenchido', async () => {
    const calls = stubApi({
      'GET /api/profile': () => json(200, { profile: null, assessment: assessment(1) }),
      'POST /api/profile/import/preview': () =>
        json(200, {
          draft: {
            role: 'Desenvolvedor de Software Java',
            primaryTechnology: 'Java',
            secondaryTechnologies: ['Spring Boot', 'Kafka'],
            flexibleTechnologies: [],
            currentSeniority: 'pleno',
            targetLevels: ['pleno'],
            priorities: [],
            juniorFloorCltMonthlyBrl: null,
            juniorFloorPjMonthlyBrl: null,
          },
          warnings: [{ field: 'role', code: 'inferred', message: 'Cargo inferido do currículo.' }],
          provenance: {
            role: { source: 'inferred', line: 5, excerpt: 'Desenvolvedor de Software Java' },
            primaryTechnology: {
              source: 'inferred',
              line: 12,
              excerpt: 'Java, Spring Boot, Kafka',
            },
          },
        }),
    });
    openAt('/perfil');

    const input = await screen.findByLabelText('Arquivo do currículo (.pdf, .md ou .txt)');
    const pdf = new File(['%PDF-1.4'], 'curriculo.pdf', { type: 'application/pdf' });
    await userEvent.upload(input, pdf);
    expect(screen.getByText('PDF selecionado: curriculo.pdf')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Ler e revisar' }));

    const form = await screen.findByRole('form', { name: 'Revisão do perfil' });
    expect(within(form).getByLabelText('Cargo desejado')).toHaveValue(
      'Desenvolvedor de Software Java',
    );
    expect(within(form).getByLabelText('Tecnologia principal')).toHaveValue('Java');
    expect(within(form).getByLabelText('Tecnologias secundárias (uma por linha)')).toHaveValue(
      'Spring Boot\nKafka',
    );
    const previewCall = calls.find((c) => c.key === 'POST /api/profile/import/preview');
    expect(previewCall?.body).toBeInstanceOf(FormData);
    expect((previewCall?.body as FormData).get('file')).toBe(pdf);
  });

  it('abre rascunho de servidor sem origem por campo e marca correções no envio', async () => {
    let profile: ReturnType<typeof savedProfile> | null = null;
    const calls = stubApi({
      'GET /api/profile': () => json(200, { profile, assessment: assessment(1) }),
      'POST /api/profile/import/preview': () =>
        json(200, {
          draft: {
            role: 'Desenvolvedor Backend Java',
            primaryTechnology: 'Java',
            secondaryTechnologies: ['Spring Boot', 'Angular'],
            flexibleTechnologies: [],
            currentSeniority: 'pleno',
            targetLevels: [],
            priorities: [],
            juniorFloorCltMonthlyBrl: null,
            juniorFloorPjMonthlyBrl: null,
          },
          warnings: [
            { field: 'role', code: 'inferred', message: 'Cargo lido do título.' },
            { field: 'targetLevels', code: 'missing', message: 'Sem níveis.' },
          ],
        }),
      'PUT /api/profile': (init) => {
        const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
        profile = savedProfile({ ...body, version: 1, updatedAt: '2026-11-21T12:00:00Z' });
        return json(200, { profile, assessment: assessment(2, 'pending') });
      },
    });
    openAt('/perfil');

    await userEvent.type(
      await screen.findByLabelText('Ou cole o texto do currículo'),
      '# Ana{enter}**Dev**',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Ler e revisar' }));

    const form = await screen.findByRole('form', { name: 'Revisão do perfil' });
    expect(within(form).getByText('Nada foi salvo ainda.', { exact: false })).toBeVisible();
    expect(within(form).getByText('Deduzido do currículo — confira')).toBeVisible();
    expect(within(form).getByText('Não encontrado no currículo — preencha')).toBeVisible();
    expect(within(form).getAllByText('Lido do currículo').length).toBeGreaterThan(0);
    expect(calls.some((c) => c.key === 'PUT /api/profile')).toBe(false);

    const primary = within(form).getByLabelText('Tecnologia principal');
    await userEvent.clear(primary);
    await userEvent.type(primary, 'Java 21');
    expect(within(form).getByText('Corrigido por você')).toBeVisible();

    await userEvent.click(within(form).getByRole('checkbox', { name: 'Sênior' }));
    await userEvent.type(within(form).getByLabelText('Piso Júnior CLT'), '8.000,50');
    await userEvent.type(
      within(form).getByLabelText('Empresas prioritárias, em ordem (uma por linha)'),
      'SAP{enter}CWI',
    );
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar perfil' }));

    expect(await screen.findByText('✓ Perfil salvo (v1).')).toBeVisible();
    const put = calls.find((c) => c.key === 'PUT /api/profile');
    expect(put?.body).toEqual({
      version: 0,
      origin: 'markdown',
      role: 'Desenvolvedor Backend Java',
      primaryTechnology: 'Java 21',
      secondaryTechnologies: ['Spring Boot', 'Angular'],
      flexibleTechnologies: [],
      currentSeniority: 'pleno',
      targetLevels: ['senior'],
      priorities: ['SAP', 'CWI'],
      juniorFloorCltMonthlyBrl: 8000.5,
      juniorFloorPjMonthlyBrl: null,
      provenance: {
        primaryTechnology: { source: 'edited', line: null, excerpt: null },
        targetLevels: { source: 'declared', line: null, excerpt: null },
        priorities: { source: 'declared', line: null, excerpt: null },
        juniorFloorCltMonthlyBrl: { source: 'declared', line: null, excerpt: null },
      },
    });
    const summary = screen.getByRole('region', { name: 'Perfil salvo' });
    expect(
      within(summary).getByText('Importado de currículo e revisado', { exact: false }),
    ).toBeVisible();
    expect(within(summary).getByText('SAP, CWI')).toBeVisible();
  });

  it('mostra a linha de evidência, preserva a referência ao corrigir e reexibe após salvar', async () => {
    let profile: ReturnType<typeof savedProfile> | null = null;
    const evidence = { line: 3, excerpt: 'Dev Backend | Java, Spring Boot' };
    const calls = stubApi({
      'GET /api/profile': () => json(200, { profile, assessment: assessment(1) }),
      'POST /api/profile/import/preview': () =>
        json(200, {
          draft: {
            role: 'Dev Backend',
            primaryTechnology: 'Java',
            secondaryTechnologies: ['Spring Boot'],
            flexibleTechnologies: [],
            currentSeniority: 'pleno',
            targetLevels: ['pleno'],
            priorities: [],
            juniorFloorCltMonthlyBrl: 8000,
            juniorFloorPjMonthlyBrl: null,
          },
          warnings: [{ field: 'role', code: 'inferred', message: 'Cargo da manchete.' }],
          provenance: {
            role: { source: 'inferred', ...evidence },
            primaryTechnology: { source: 'inferred', ...evidence },
            secondaryTechnologies: { source: 'inferred', ...evidence },
            currentSeniority: { source: 'imported', line: 5, excerpt: 'Senioridade: Pleno' },
            targetLevels: { source: 'imported', line: 6, excerpt: 'Níveis alvo: Pleno' },
            juniorFloorCltMonthlyBrl: {
              source: 'imported',
              line: 7,
              excerpt: 'Piso júnior CLT: 8000',
            },
          },
        }),
      'PUT /api/profile': (init) => {
        const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
        profile = savedProfile({ ...body, version: 1 });
        return json(200, { profile, assessment: assessment(2, 'pending') });
      },
    });
    openAt('/perfil');

    await userEvent.type(await screen.findByLabelText('Ou cole o texto do currículo'), '# Ana');
    await userEvent.click(screen.getByRole('button', { name: 'Ler e revisar' }));
    const form = await screen.findByRole('form', { name: 'Revisão do perfil' });
    expect(within(form).getAllByText('Deduzido do currículo — confira')).toHaveLength(3);
    expect(within(form).getAllByText('Linha 3: “Dev Backend | Java, Spring Boot”')).toHaveLength(3);
    expect(within(form).getByText('Linha 5: “Senioridade: Pleno”')).toBeVisible();

    const primary = within(form).getByLabelText('Tecnologia principal');
    await userEvent.clear(primary);
    await userEvent.type(primary, 'Kotlin');
    expect(within(form).getByText('Corrigido por você')).toBeVisible();
    expect(
      within(form).getByText('Original na linha 3: “Dev Backend | Java, Spring Boot”'),
    ).toBeVisible();
    await userEvent.type(within(form).getByLabelText('Piso Júnior PJ'), '12000');
    expect(within(form).getByText('Informado por você')).toBeVisible();
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar perfil' }));

    expect(await screen.findByText('✓ Perfil salvo (v1).')).toBeVisible();
    const put = calls.find((c) => c.key === 'PUT /api/profile')?.body as Record<string, unknown>;
    expect(put.provenance).toEqual({
      role: { source: 'inferred', ...evidence },
      primaryTechnology: { source: 'edited', ...evidence },
      secondaryTechnologies: { source: 'inferred', ...evidence },
      currentSeniority: { source: 'imported', line: 5, excerpt: 'Senioridade: Pleno' },
      targetLevels: { source: 'imported', line: 6, excerpt: 'Níveis alvo: Pleno' },
      juniorFloorCltMonthlyBrl: { source: 'imported', line: 7, excerpt: 'Piso júnior CLT: 8000' },
      juniorFloorPjMonthlyBrl: { source: 'declared', line: null, excerpt: null },
    });
    expect(JSON.stringify(put)).not.toContain('# Ana');

    const summary = screen.getByRole('region', { name: 'Perfil salvo' });
    expect(within(summary).getByText('Corrigido por você')).toBeVisible();
    expect(within(summary).getByText('Informado por você')).toBeVisible();
    expect(within(summary).getByText('Linha 7: “Piso júnior CLT: 8000”')).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: 'Editar perfil' }));
    const edit = screen.getByRole('form', { name: 'Revisão do perfil' });
    expect(
      within(edit).getByText('Original na linha 3: “Dev Backend | Java, Spring Boot”'),
    ).toBeVisible();
  });

  it('em falha de leitura oferece entrada manual e salva no mesmo contrato', async () => {
    const calls = stubApi({
      'GET /api/profile': () => json(200, { profile: null, assessment: assessment(1) }),
      'POST /api/profile/import/preview': () =>
        json(400, { error: { code: 'invalid_profile_markdown', message: 'Markdown inválido.' } }),
      'PUT /api/profile': () =>
        json(200, {
          profile: savedProfile({ origin: 'manual', targetLevels: ['pleno'] }),
          assessment: assessment(2, 'pending'),
        }),
    });
    openAt('/perfil');

    await userEvent.type(await screen.findByLabelText('Ou cole o texto do currículo'), 'texto');
    await userEvent.click(screen.getByRole('button', { name: 'Ler e revisar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Markdown inválido. Você pode preencher o perfil manualmente',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Preencher manualmente' }));

    const form = await screen.findByRole('form', { name: 'Revisão do perfil' });
    expect(within(form).queryByText('Lido do currículo')).not.toBeInTheDocument();
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar perfil' }));
    expect(within(form).getByRole('alert')).toHaveTextContent('Informe o cargo desejado.');
    expect(calls.some((c) => c.key === 'PUT /api/profile')).toBe(false);

    await userEvent.type(within(form).getByLabelText('Cargo desejado'), 'Dev Java');
    await userEvent.type(within(form).getByLabelText('Tecnologia principal'), 'Java');
    await userEvent.selectOptions(within(form).getByLabelText('Senioridade atual'), 'pleno');
    await userEvent.click(within(form).getByRole('checkbox', { name: 'Pleno' }));
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar perfil' }));

    expect(await screen.findByText('✓ Perfil salvo (v1).')).toBeVisible();
    const body = calls.find((c) => c.key === 'PUT /api/profile')?.body as Record<string, unknown>;
    expect(body).toMatchObject({ version: 0, origin: 'manual', role: 'Dev Java' });
    expect(body.provenance).toEqual({
      role: { source: 'declared', line: null, excerpt: null },
      primaryTechnology: { source: 'declared', line: null, excerpt: null },
      currentSeniority: { source: 'declared', line: null, excerpt: null },
      targetLevels: { source: 'declared', line: null, excerpt: null },
    });
  });

  it('edita perfil salvo com a versão atual e mantém edições em conflito', async () => {
    let conflict = true;
    const calls = stubApi({
      'GET /api/profile': () =>
        json(200, {
          profile: savedProfile({
            version: conflict ? 3 : 4,
            secondaryTechnologies: conflict ? ['Spring Boot'] : ['Redis'],
          }),
          assessment: assessment(4),
        }),
      'PUT /api/profile': () => {
        if (conflict) {
          conflict = false;
          return json(409, {
            error: { code: 'profile_version_conflict', message: 'Perfil mudou em outra aba.' },
          });
        }
        return json(200, { profile: savedProfile({ version: 5 }), assessment: assessment(5) });
      },
    });
    openAt('/perfil');

    await userEvent.click(await screen.findByRole('button', { name: 'Editar perfil' }));
    const form = screen.getByRole('form', { name: 'Revisão do perfil' });
    expect(within(form).getByText('Origem: importação de currículo')).toBeVisible();
    const secondary = within(form).getByLabelText('Tecnologias secundárias (uma por linha)');
    await userEvent.type(secondary, '{enter}Docker');
    await userEvent.click(within(form).getByRole('button', { name: 'Salvar perfil' }));

    expect(await within(form).findByRole('alert')).toHaveTextContent(
      'Perfil mudou em outra aba. Suas edições permanecem no formulário.',
    );
    expect(secondary).toHaveValue('Spring Boot\nDocker');
    await userEvent.click(within(form).getByRole('button', { name: 'Recarregar versão atual' }));
    expect(await screen.findByDisplayValue('Redis')).toBeVisible();
    await userEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));

    expect(await screen.findByText('✓ Perfil salvo (v5).')).toBeVisible();
    const puts = calls.filter((c) => c.key === 'PUT /api/profile');
    expect((puts[0].body as { version: number }).version).toBe(3);
    expect((puts[1].body as { version: number }).version).toBe(4);
    expect((puts[1].body as { secondaryTechnologies: string[] }).secondaryTechnologies).toEqual([
      'Redis',
    ]);
  });
});

describe('regras do formulário de perfil', () => {
  it.each([
    ['', null],
    ['8000', 8000],
    ['8.000,50', 8000.5],
    ['12000.5', 12000.5],
    ['0', 'invalid'],
    ['-1', 'invalid'],
    ['abc', 'invalid'],
    ['10,555', 'invalid'],
  ])('interpreta piso %j como %j', (text, expected) => {
    expect(parseFloor(text)).toEqual(expected);
  });

  it('rejeita tecnologia repetida entre principal, secundárias e flexíveis', () => {
    const form = toForm(
      parseProfileView({ profile: savedProfile(), assessment: assessment(1) }).profile!,
    );
    const result = validate({
      ...form,
      secondaryTechnologies: 'java',
      flexibleTechnologies: 'JAVA',
    });
    expect(result).toEqual({
      errors: [
        'A tecnologia principal não pode repetir nas secundárias.',
        'Tecnologias flexíveis não podem repetir a principal nem as secundárias.',
      ],
    });
  });

  it('aceita piso como texto decimal e rejeita perfil malformado', () => {
    const view = parseProfileView({
      profile: savedProfile({ juniorFloorCltMonthlyBrl: '8000.00' }),
      assessment: assessment(1),
    });
    expect(view.profile?.juniorFloorCltMonthlyBrl).toBe(8000);
    expect(() =>
      parseProfileView({
        profile: savedProfile({ targetLevels: ['lead'] }),
        assessment: assessment(1),
      }),
    ).toThrow();
    expect(parsePreparation(undefined)).toBeNull();
  });

  it('aceita respostas sem origem por campo e rejeita origem malformada', () => {
    const legacy = parseProfileView({ profile: savedProfile(), assessment: assessment(1) });
    expect(legacy.profile?.provenance).toEqual({});
    const preview = {
      draft: { ...savedProfile(), role: null, primaryTechnology: null, currentSeniority: null },
      warnings: [],
    };
    expect(parseProfilePreview(preview).provenance).toEqual({});
    expect(() =>
      parseProfilePreview({ ...preview, provenance: { salary: { source: 'imported' } } }),
    ).toThrow();
    expect(() =>
      parseProfilePreview({ ...preview, provenance: { role: { source: 'guessed', line: null } } }),
    ).toThrow();
    expect(() =>
      parseProfilePreview({ ...preview, provenance: { role: { source: 'imported', line: 0 } } }),
    ).toThrow();
  });

  it('mantém a origem de campos não alterados e não declara campos vazios', () => {
    const form = toForm(
      parseProfileView({ profile: savedProfile(), assessment: assessment(1) }).profile!,
    );
    const session = {
      origin: 'markdown' as const,
      initial: form,
      warnings: [],
      provenance: { role: { source: 'imported' as const, line: 2, excerpt: 'Cargo: Dev' } },
      imported: false,
    };
    expect(provenanceFor(session, form)).toEqual({
      role: { source: 'imported', line: 2, excerpt: 'Cargo: Dev' },
    });
    expect(provenanceFor(session, { ...form, role: 'Outro', priorities: '' })).toEqual({
      role: { source: 'edited', line: 2, excerpt: 'Cargo: Dev' },
    });
  });
});

// ---- Detalhe: preparação e exceção Júnior ----

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: 'job-1',
    title: 'Desenvolvedor Java Júnior',
    companyName: 'SAP',
    location: 'Remoto',
    workMode: 'remote',
    contractType: 'clt',
    salary: { min: 9000, max: 11000, unit: 'monthly' },
    fitReasons: ['Justificativa para júnior: SAP tem programa de carreira (RB-004).'],
    userDecision: 'none',
    availability: 'open',
    compatibility: {
      criteriaVersion: 2,
      evaluatorVersion: 1,
      assessedAt: '2026-11-21T12:00:00Z',
      current: true,
      roleStatus: 'matched',
      technologyStatus: 'matched',
      reasonCodes: [],
    },
    compatibilityCriteria: null,
    compatibilityEvidence: [],
    facts: [],
    inferred: [],
    missing: [],
    sources: [],
    seniority: { label: 'Júnior', status: 'fact', reason: null },
    corrections: [],
    timeline: [],
    preparation: {
      profileVersion: 1,
      required: [{ technology: 'Java', profileTier: 'primary', quote: 'Java 17 obrigatório' }],
      optional: [{ technology: 'Docker', profileTier: 'none', quote: 'Docker é um diferencial' }],
      mentioned: [{ technology: 'Kafka', profileTier: 'none', quote: 'O time usa Kafka' }],
    },
    ...overrides,
  };
}

function region(name: string) {
  return screen.getByRole('region', { name });
}

describe('preparação vinculada ao anúncio', () => {
  it('agrupa por exigência, cita a evidência e reflete o perfil após edição', async () => {
    let profile = savedProfile();
    let prep = detail().preparation;
    stubApi({
      'GET /api/opportunities/job-1': () => json(200, detail({ preparation: prep })),
      'GET /api/profile': () => json(200, { profile, assessment: assessment(2) }),
      'PUT /api/profile': () => {
        profile = savedProfile({ version: 2, secondaryTechnologies: ['Spring Boot', 'Docker'] });
        prep = {
          ...prep,
          profileVersion: 2,
          optional: [
            { technology: 'Docker', profileTier: 'secondary', quote: 'Docker é um diferencial' },
          ],
        };
        return json(200, { profile, assessment: assessment(3, 'pending') });
      },
    });
    openAt('/vagas/job-1');

    await screen.findByRole('heading', { level: 1, name: 'Desenvolvedor Java Júnior' });
    const required = region('Exigência obrigatória');
    expect(within(required).getByText('— sua tecnologia principal')).toBeVisible();
    expect(within(required).getByText('“Java 17 obrigatório”', { exact: false })).toBeVisible();
    const optional = region('Diferencial');
    expect(within(optional).getByText('— não consta no perfil: estudar')).toBeVisible();
    expect(within(region('Mencionada pela equipe')).getByText('Kafka')).toBeVisible();
    expect(screen.getByText('Comparado com o seu perfil v1.', { exact: false })).toBeVisible();

    await userEvent.click(screen.getByRole('link', { name: 'Editar perfil →' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Editar perfil' }));
    await userEvent.type(
      screen.getByLabelText('Tecnologias secundárias (uma por linha)'),
      '{enter}Docker',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Salvar perfil' }));
    await screen.findByText('✓ Perfil salvo (v2).');

    window.history.pushState({}, '', '/vagas/job-1');
    window.dispatchEvent(new PopStateEvent('popstate'));
    expect(await screen.findByText('— no perfil como secundária')).toBeVisible();
    expect(screen.getByText('Comparado com o seu perfil v2.', { exact: false })).toBeVisible();
  });

  it('sem perfil salvo orienta o cadastro', async () => {
    stubApi({
      'GET /api/opportunities/job-1': () =>
        json(
          200,
          detail({
            preparation: { profileVersion: null, required: [], optional: [], mentioned: [] },
          }),
        ),
      'GET /api/profile': () => json(200, { profile: null, assessment: assessment(1) }),
    });
    openAt('/vagas/job-1');
    expect(await screen.findByRole('link', { name: 'Cadastrar perfil →' })).toBeVisible();
    expect(screen.getByText('Nenhuma tecnologia identificada no anúncio.')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Exceção Júnior' })).not.toBeInTheDocument();
  });
});

describe('exceção Júnior no detalhe', () => {
  async function exceptionFor(job: Record<string, unknown>, profile = savedProfile()) {
    stubApi({
      'GET /api/opportunities/job-1': () => json(200, detail(job)),
      'GET /api/profile': () => json(200, { profile, assessment: assessment(2) }),
    });
    openAt('/vagas/job-1');
    await screen.findByRole('heading', { level: 1, name: 'Desenvolvedor Java Júnior' });
    return screen.findByRole('region', { name: 'Exceção Júnior' });
  }

  it('explica salário confirmado acima do piso e a justificativa da empresa', async () => {
    const section = await exceptionFor({
      fitReasons: ['Exceção júnior: SAP tem programa de carreira (RB-004).'],
    });
    expect(section).toHaveTextContent('Atende às três condições da exceção.');
    expect(section).toHaveTextContent('Mínimo anunciado de R$ 9.000,00 atinge o piso Júnior CLT');
    expect(section).toHaveTextContent('SAP tem programa de carreira (RB-004).');
  });

  it.each([
    [{ salary: null }, 'Salário mensal não anunciado; estimativas não confirmam'],
    [{ salary: { min: 7000, max: 9000, unit: 'monthly' } }, 'A faixa anunciada cruza o piso'],
    [{ salary: { min: 5000, max: 6000, unit: 'monthly' } }, 'fica abaixo do piso'],
    [{ contractType: 'pj' }, 'Você não definiu piso Júnior PJ.'],
    [{ contractType: 'unknown' }, 'Regime da vaga (CLT ou PJ) não informado.'],
    [{ fitReasons: [] }, 'A avaliação atual não informa a justificativa'],
    [
      {
        compatibility: {
          ...detail().compatibility,
          technologyStatus: 'mismatched',
        },
      },
      'Cargo ou tecnologia não confirmados',
    ],
  ])('mostra condição não atendida %#', async (job, reason) => {
    const section = await exceptionFor(job);
    expect(section).toHaveTextContent('não confirmou uma decisão');
    expect(section).toHaveTextContent(reason);
  });

  it('usa o veredito do serviço quando a avaliação atual exclui a vaga', async () => {
    const section = await exceptionFor({
      salary: null,
      fitReasons: [
        'Vaga júnior excluída; falta salário anunciado confirmado acima do piso júnior, justificativa júnior da empresa.',
      ],
    });
    expect(section).toHaveTextContent('Não atende a todas as condições');
    expect(section).toHaveTextContent('Avaliação do serviço: Vaga júnior excluída; falta salário');
    expect(section).toHaveTextContent('Ser prioritária, sozinha, não qualifica.');
  });

  it('aceita a justificativa emitida pela exceção com perfil salvo', async () => {
    const section = await exceptionFor({
      fitReasons: ['Exceção júnior: SAP contrata Júnior com plano.'],
    });
    expect(section).toHaveTextContent('Atende às três condições da exceção.');
    expect(section).toHaveTextContent('SAP contrata Júnior com plano.');
  });

  it('não aparece quando a pessoa busca Júnior ou a vaga não é Júnior', async () => {
    stubApi({
      'GET /api/opportunities/job-1': () => json(200, detail()),
      'GET /api/profile': () =>
        json(200, {
          profile: savedProfile({ targetLevels: ['junior', 'pleno'] }),
          assessment: assessment(2),
        }),
    });
    openAt('/vagas/job-1');
    await screen.findByRole('heading', { level: 1, name: 'Desenvolvedor Java Júnior' });
    await screen.findByText('Comparado com o seu perfil v1.', { exact: false });
    expect(screen.queryByRole('region', { name: 'Exceção Júnior' })).not.toBeInTheDocument();
  });
});
