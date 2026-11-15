# Pesquisa — cores dos cards por empresa

## 1. Ponto de extensão e escopo

**Decision**: manter `src/lib/opportunity.ts` como ponto único de resolução, com `OpportunityCard` aplicando `--c` nos destaques existentes.

**Rationale**: a inspeção local encontrou mapeamento parcial para Itaú, SAP, PicPay e Sicredi, fallback grafite e consumo compartilhado por Hoje/listas. `panel.css` e `index.css` já desenham bordas e hachuras com `--c`. Ampliar essa regra atende ao pedido sem rede, estado ou tema novo.

**Alternatives considered**: serviço de cores/logos, nova biblioteca, persistência e mapeamento por página rejeitados por custo e divergência desnecessários.

## 2. Correspondência e estabilidade

**Decision**: chave `trim().toLowerCase()`, tabela exata, aliases explícitos e fallback por hash unsigned de 32 bits sobre chave normalizada para paleta fixa. Detalhes em [data-model.md](data-model.md).

**Rationale**: regex atuais de prefixo podem atribuir marca a nome diferente. Exatidão evita esse problema; hash independe de posição, histórico, filtro ou sessão. Alias inicial adicional permitido: `itau` para `itaú`; não inferir grupos econômicos.

**Alternatives considered**: cor aleatória ou por índice rompe estabilidade; uma cor neutra para todas as desconhecidas não atende FR-004; normalização aproximada pode confundir organizações. Hash permite colisões, aceitas explicitamente pela especificação.

## 3. Cores e fontes

**Decision**: preservar tons existentes como escolhas locais históricas (sem certificá-los como oficiais): Itaú #EC7000, SAP #0070F2, PicPay #21C25E, Sicredi #2F7D12. Adotar para marcas adicionais valores de ativos oficiais quando disponíveis. Não representar tom proposto como hex oficial de manual de marca.

Pesquisa em 2026-11-15 por agente focado em fontes primárias:

| Empresa | Tom para destaques | Evidência / qualificação |
| --- | --- | --- |
| BV | #005CA9 | Proposta visual aproximada do produto; hex oficial não confirmado. Fontes institucionais: https://www.bv.com.br/pt/web/portal/institucional/o-banco e https://www.bv.com.br/institucional/imprensa |
| CWI | #FF8A00 | CSS oficial define `--primary-orange`: https://cwi.com.br/wp-content/themes/cwi-theme/dist/app.css?ver=d946f2fbc77cf4466e96 |
| DB | #F70773 | Valor usado no HTML oficial https://db.tec.br/pt ; escolha baseada no site, não certificação por manual |
| CI&T | #FA5A50 | `fill` do logo SVG oficial https://ciandt.com/themes/custom/ciandt_theme/logo.svg |

**Rationale**: os acentos reproduzem identidade reconhecível sem exigir texto na cor de marca. Ativo publicado não equivale a manual normativo; os nomes permanecem a identificação primária.

**Alternatives considered**: extrair marca em runtime rejeitado por rede, proveniência e instabilidade; usar cor de marca para texto rejeitado por contraste. Os valores são decorativos, sujeitos à verificação visual prevista.

## 4. Acessibilidade e validação

**Decision**: conservar texto escuro/fundo claro e foco independente da marca; validar com testes unitários, de componente e Playwright isolado, axe e inspeção dirigida de pseudo-elementos.

**Rationale**: [W3C — contraste mínimo](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) estabelece 4,5:1 para texto normal e 3:1 para texto grande. Cor decorativa não elimina obrigação de contraste no texto ou identificação por rótulo. Axe não comprova sozinho foco, overflow e todo desenho de hachuras.

**Alternatives considered**: mudar globalmente todos os tokens, usar texto branco sobre marcas ou confiar apenas em screenshot/axe rejeitados. A suíte isolada existente já simula APIs; ampliar seus cenários evita banco pessoal e coleta externa.

## 5. Ferramentas e limites

**Decision**: usar scripts npm e ferramentas já presentes; nenhuma dependência ou versão será adicionada/atualizada. Preservar contrato HTTP, JPA e SQLite existentes.

**Rationale**: frontend usa CSS próprio e package-lock; impor Tailwind, shadcn ou pnpm seria migração alheia ao requisito. Setup do Spec Kit informou `003-company-card-colors` como contexto lógico, mas `git branch --show-current` confirmou `main`; não anunciar branch criada.

**Alternatives considered**: backend fornecer cor ou persistir preferências rejeitados porque não há requisito de configuração manual nem identidade de empresa nova. Testar correção/navegação com fixtures, sem criar novos filtros ou ordenação.


## Resultado da fase 0

Decisões técnicas resolvidas. BV usa tom aproximado explicitamente documentado, sem bloquear o requisito de uma cor definida. Nenhum manual normativo foi certificado. Medições de contraste e validação da aplicação pertencem à implementação. Hooks before_plan/after_plan não executados: .specify/extensions.yml não existe.
