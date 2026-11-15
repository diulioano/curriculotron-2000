# Modelo de dados — cores dos cards

## Dados existentes

`Opportunity.companyName: string` permanece no contrato HTTP atual. Não adicionar coluna, tabela, DTO, endpoint nem campo de cor à resposta. Nome vazio ou apenas espaços é ausência para apresentação; null não faz parte do tipo atual e não será legitimado por cast.

## Valores de apresentação

- **Chave de empresa**: `companyName.trim().toLowerCase()`. Não remover acentos, pontuação, espaços internos nem sufixos automaticamente. Nomes semelhantes não são aliases implícitos.
- **Registro de marca**: chave exata (ou alias explicitamente aprovado) e cor hexadecimal. Registro estático, sem entidade persistida. Preservar Itaú/Itaú sem acento via aliases explícitos quando necessário.
- **Paleta alternativa**: lista fixa ordenada de cores decorativas. Não inclui grafite reservado à ausência. Colisões entre empresas são permitidas.
- **Cor resolvida**: string hexadecimal no formato `#RRGGBB`, compatível com `companyColor(name: string): string` existente. Não expor rótulo de cor “oficial” na interface.

## Relações e invariantes

Uma empresa pode ter muitos cards; todos derivam a cor da mesma chave. Chave vazia resulta em `GRAPHITE = #6B6B73`. Chave conhecida usa tabela; outra usa hash determinístico sobre unidades UTF-16 da chave: acumulador inicial zero, atualização unsigned de 32 bits por `31 * acumulador + unidade`, índice como resto pelo tamanho da paleta. Não usar sorteio, ordem de renderização ou locale variável.

Paleta candidata fixa: `#475569`, `#7C3AED`, `#0F766E`, `#B45309`, `#BE185D`, `#0369A1`. São acentos decorativos; textos continuam no token de tinta. Não há garantia de unicidade. Ordem e algoritmo passam a ser parte da estabilidade; mudanças futuras exigem avaliação explícita.

## Transições

Não há novo estado. Filtrar, reordenar, descartar e restaurar não alteram a chave. Corrigir a empresa recalcula a cor no render seguinte. Reiniciar o aplicativo conserva a identidade porque tabela, paleta e algoritmo são estáticos. Não criar Effects ou cache paralelo.
