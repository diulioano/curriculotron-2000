# Contrato HTTP — US006 Perfil, descoberta e estudo

Backend é a fonte deste contrato. Todas as rotas exigem sessão; mutações exigem CSRF
(cliente existente). Erros usam o envelope `{"error":{"code","message"}}`.

## Tipos

```ts
type Seniority = "junior" | "pleno" | "senior";
type ProfileOrigin = "markdown" | "manual";
type ProfileField = keyof ProfileDraft; // os 9 campos editáveis

// Origem de um campo. imported = lido de rótulo/seção do Markdown; inferred = deduzido do
// currículo; edited = valor importado/inferido corrigido pela pessoa; declared = informado pela
// pessoa sem evidência documental.
type FieldSource = "imported" | "inferred" | "edited" | "declared";
interface FieldProvenance {
  source: FieldSource;
  line: number | null;     // linha 1-based do Markdown usada como evidência
  excerpt: string | null;  // trecho dessa linha, 1..200 chars (cortado com "…")
}
type ProfileProvenance = Partial<Record<ProfileField, FieldProvenance>>;

interface CareerProfile {
  version: number;                    // >= 1; otimista no PUT
  role: string;                       // cargo, 1..200
  primaryTechnology: string;          // 1..100
  secondaryTechnologies: string[];    // <= 30, sem repetir a principal
  flexibleTechnologies: string[];     // <= 30, sem repetir principal/secundárias
  currentSeniority: Seniority;
  targetLevels: Seniority[];          // 1..3, sem repetição
  priorities: string[];               // ordem = prioridade, <= 20, itens 1..200
  juniorFloorCltMonthlyBrl: number | null; // BRL mensal, > 0, até 2 casas
  juniorFloorPjMonthlyBrl: number | null;
  origin: ProfileOrigin;
  provenance: ProfileProvenance;      // sempre objeto; {} sem origem registrada
  updatedAt: string;                  // ISO instant
}

// CompatibilityStatus é o mesmo objeto `assessment` de GET /api/criteria.
interface ProfileView {
  profile: CareerProfile | null;
  assessment: CompatibilityStatus;
  priorityCompanies: string[];
}
```

Valores monetários trafegam como número JSON (BigDecimal), igual a `/api/criteria`.

## GET /api/profile

200 `ProfileView`. `profile: null` enquanto nada foi salvo.

## POST /api/profile/import/preview

Converte currículo (PDF, Markdown ou texto puro) em rascunho editável. **Não grava nada.**

Request (dois formatos suportados):
- `application/json`: `{ "markdown": string }` (1..100000 chars).
- `multipart/form-data`: parte `file` com currículo em `.pdf` (até 5 MB e 30 páginas, com texto selecionável), `.md`, `.markdown` ou `.txt` (UTF-8).

200:
```ts
interface ProfileDraft {
  role: string | null;
  primaryTechnology: string | null;
  secondaryTechnologies: string[];
  flexibleTechnologies: string[];
  currentSeniority: Seniority | null;
  targetLevels: Seniority[];
  priorities: string[];
  juniorFloorCltMonthlyBrl: number | null;
  juniorFloorPjMonthlyBrl: number | null;
}
interface ProfilePreview {
  draft: ProfileDraft;
  warnings: { field: keyof ProfileDraft; code: "missing" | "inferred" | "invalid"; message: string }[];
  provenance: ProfileProvenance; // só campos preenchidos; source imported | inferred
}
```

A prévia devolve apenas a linha e o trecho curto que serviram de evidência para cada campo,
nunca o Markdown recebido. Campos ausentes ou com valor inválido ficam fora de `provenance`
(os `warnings` continuam iguais). Níveis alvo deduzidos apontam a linha da senioridade.

Formato reconhecido (rótulos sem acento/maiúsculas também valem, com ou sem `-`/`**`):

```md
- Cargo: Desenvolvedor Backend Java
- Tecnologia principal: Java
- Tecnologias secundárias: Spring Boot, Angular
- Tecnologias flexíveis: Kotlin, Go
- Senioridade atual: Pleno
- Níveis alvo: Pleno, Sênior
- Prioridades: SAP, CI&T
- Piso júnior CLT: 8000
- Piso júnior PJ: 12000,50
```

Também aceita seção `## <rótulo>` seguida de itens `- valor`. Sem rótulos, usa o currículo:
linha em negrito `**Cargo | Tec1, Tec2 & Tec3**` sob o `#` (cargo, principal e secundárias) e
`mid-level`/`pleno`/`júnior`/`sênior` no texto (senioridade). Campos assim vêm com warning
`inferred`; campos ausentes vêm com `missing`. A pessoa edita o rascunho e salva com o PUT.

400 `invalid_profile_markdown` para corpo JSON ausente/vazio/grande demais.
400 `invalid_profile_document` para arquivo vazio, formato não suportado, PDF inválido/protegido/escaneado ou acima do limite de páginas/texto.
413 `invalid_profile_document` para arquivo acima de 5 MB.

## PUT /api/profile

Salva importação revisada ou edição manual (mesmo contrato).

Request: `CareerProfile` sem `updatedAt`; `version` = versão atual (0 quando `profile` é null).
`provenance` é opcional: ausente ou `null` salva `{}` (clientes anteriores continuam válidos).
O cliente envia a origem que exibiu: campo inalterado mantém a da prévia/perfil; campo alterado
com evidência vira `edited` mantendo `line`/`excerpt`; campo preenchido sem evidência vira
`declared`. Regras: `line` e `excerpt` juntos ou ambos `null`; `imported` exige referência;
`declared` não aceita referência; `line` 1..100000. Campo desconhecido ou regra violada → 400
`invalid_profile`.

200 `ProfileView` com a nova versão. Salvar valores idênticos não cria versão — nem atualiza
`origin`/`provenance`, que não entram na comparação de conteúdo.
400 `invalid_profile` (validação), 409 `profile_version_conflict` (versão desatualizada).

Efeitos: cargo vira `targetRoles = [role]` e tecnologias (principal, secundárias, flexíveis)
viram `stackKeywords` dos critérios; a versão dos critérios sobe e a reavaliação de
compatibilidade fica `pending` (mesmo fluxo de `PUT /api/criteria`). `GET /api/criteria`
reflete esses campos; editar critérios depois continua possível.

A resposta do perfil inclui `priorityCompanies`, as empresas já priorizadas na instalação. No primeiro cadastro, o formulário começa com essas empresas quando o currículo não traz uma lista. A lista revisada substitui as prioridades do cadastro na mesma transação do perfil. Empresas removidas continuam disponíveis para busca. As fontes e as justificativas registradas permanecem vinculadas à empresa.
Para aplicar o novo perfil às vagas, o cliente chama `POST /api/opportunities/reassess` com
`assessment.criteriaVersion`, como já faz após salvar critérios. A preparação do detalhe não
depende disso.

## Ranking (efeito em listas existentes)

- Nota (`fitScore`) só com trabalho e stack: base 60; +10 remoto preferido; +10 salário
  confirmado; +15 se a principal aparece como obrigatória/diferencial/uso, senão +8 se uma
  secundária aparece. Flexíveis contam como fit sem bônus.
- Empresa prioritária não soma pontos: só desempata nota igual (`selection`/`secondary`).
- Empresas fora da lista prioritária continuam elegíveis (a busca não se limita a elas; as
  fontes consultadas são as mesmas já configuradas).
- Nível da vaga (título) fora de `targetLevels` → `weak`.
- Exceção júnior (vaga júnior e `junior` fora de `targetLevels`): exige fit de cargo e stack,
  salário anunciado CLT/PJ mensal com mínimo ≥ piso júnior do regime (que prevalece sobre o mínimo
  geral dos critérios quando o piso júnior for menor) e empresa com justificativa júnior (SAP já tem).
  Faltando qualquer dado → `weak` (fora da seleção).
- Sem perfil salvo, vale a regra anterior de júnior (justificativa da empresa).

## GET /api/opportunities/{id} — campos novos `preparation` e `juniorException`

`preparation` é calculado na leitura com o perfil atual (reflete edição do perfil sem esperar reavaliação).
`juniorException` expõe a decisão tipada da exceção júnior calculada com os dados atuais e o mesmo nível do ranking.

```ts
type PreparationTier = "primary" | "secondary" | "flexible" | "none";
interface PreparationItem {
  technology: string;          // nome canônico
  profileTier: PreparationTier; // "none" = não está no perfil (estudar)
  quote: string;               // trecho da vaga
}
interface Preparation {
  profileVersion: number | null; // null sem perfil
  required: PreparationItem[];   // obrigatória
  optional: PreparationItem[];   // diferencial
  mentioned: PreparationItem[];  // menção/uso sem classificação de exigência
}

type JuniorExceptionStatus = "qualified" | "excluded" | "not_applicable" | "unknown";
type JuniorSalaryStatus =
  | "confirmed"
  | "range_crosses_floor"
  | "below_floor"
  | "not_announced"
  | "contract_unknown"
  | "floor_missing";
type JuniorSalarySource = "announced" | "estimate" | "none";
type JuniorJustificationStatus = "accepted" | "missing" | "not_accepted_by_criteria";

type JuniorException =
  | { status: "not_applicable" | "unknown"; workFit: null; salary: null; justification: null }
  | {
      status: "qualified" | "excluded";
      workFit: boolean;
      salary: {
        status: JuniorSalaryStatus;
        source: JuniorSalarySource;
        contract: string | null;
        unit: string | null;
        floor: number | null;
        announcedMin: number | null;
        announcedMax: number | null;
      };
      justification: { status: JuniorJustificationStatus; text: string | null };
    };
```

Uma tecnologia aparece em um só grupo (o mais forte). Menções negadas ou institucionais
("sobre a empresa", outra equipe) são ignoradas.


## Recomendações e histórico

`GET /api/opportunities?list=recommended` mostra vagas abertas ou a confirmar, sem decisão da pessoa e com avaliação vigente forte ou média. A lista ordena por aderência, depois por nota e empresa prioritária em caso de empate. Pode incluir vagas já vistas na seleção do dia. Ler a lista e reavaliar o perfil preservam o histórico diário. Uma nova busca consulta todas as fontes configuradas e usa o fluxo existente de seleção diária.

A comparação com a tecnologia principal admite aderência parcial entre o cargo Full Stack e uma especialidade correspondente. Um cargo alvo exato prevalece sobre a correspondência parcial. A mudança de avaliador para a versão 2 exige nova avaliação das vagas armazenadas.
