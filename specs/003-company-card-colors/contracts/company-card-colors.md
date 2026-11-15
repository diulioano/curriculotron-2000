# Contrato de apresentação — cores dos cards

## Interface local

Manter `companyColor(name: string): string` em `src/lib/opportunity.ts`, sem alteração de assinatura para consumidores existentes. Entrada é texto, saída é cor hexadecimal. Regras detalhadas em [data-model.md](../data-model.md).

- Empresa conhecida: correspondência exata após normalização; aliases apenas explícitos.
- Outra empresa identificada: cor alternativa estável, sem neutro genérico por falta de marca.
- Ausência: grafite e nome visível “Empresa não informada” no card. Iniciais ausentes continuam `?` e são decorativas.
- Empresa cujo nome apenas começa com SAP, DB etc. não recebe sua marca por prefixo.

## Interface visual

`OpportunityCard` define `--c` no article e o CSS aplica a cor nos traços de `.sk`, carimbo e hachura. Texto, link, ações e foco usam os tokens de tinta existentes. Fundo usa `--card`. Nome, status e conteúdo continuam visíveis e textuais. Não aplicar opacidade no card inteiro para estados descartados.

Superfícies cobertas: Hoje e `/vagas?list=selection`, `/vagas?list=secondary`, `/vagas?list=discarded`. O conjunto móvel inicial de Hoje mantém seu limite existente; cards adicionais continuam acessíveis pela navegação já existente.

Critérios: FR-001–FR-010 da [especificação](../spec.md). Nenhuma API externa ou endpoint novo. As mutações applied/discard/restore, seus payloads, CSRF e invalidações de cache permanecem os atuais.

## Aceitação e regressões

Testes unitários cobrem normalização, ausência, prioridades, nomes semelhantes, estabilidade e atualização por empresa corrigida. Testes de componente verificam rótulos e ações. Playwright com API simulada valida cores computadas, contorno/carimbo, navegação, reload, descarte/restauração, foco, contraste via axe e ausência de overflow em 360/1280 px. As fixtures de mutação devem atualizar as respostas de leitura para comprovar transições reais da interface.
