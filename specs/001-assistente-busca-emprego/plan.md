# Implementation Plan: Assistente pessoal de busca de emprego

**Feature**: `001-assistente-busca-emprego` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)
**Branch**: não criada; workspace sem repositório Git.
**Status**: ERROR — fase 0 bloqueada por validação técnica pendente. Não pronto para geração de tarefas de implementação.

## Summary

Assistente pessoal local com painel privado, bots revisados, descoberta de vagas, acompanhamento Gmail e preparação de entrevistas. A especificação exige evidência de viabilidade das integrações, especialmente OpenBot/Codex, antes de definir a implementação. A documentação pública confirma intenção de suporte, mas não demonstra funcionamento neste ambiente.

## Technical Context

**Language/Version**: NEEDS CLARIFICATION — decidir após validar o mecanismo de integração OpenBot.
**Primary Dependencies**: OpenBot 0.70.0 (registro local do Windows), Codex e Claude com acessos existentes do usuário, e Gmail; transporte e compatibilidade da autenticação NEEDS CLARIFICATION.
**Storage**: persistência local transacional necessária; engine ainda não selecionada.
**Testing**: cenários controlados das jornadas J1–J6 e provas de integração PT-001–PT-008; ferramentas dependem da stack.
**Target Platform**: computador Windows pessoal e navegador móvel com acesso privado.
**Project Type**: aplicação web privada, um usuário por instalação.
**Performance Goals**: dados locais em até três segundos em 95% de 20 aberturas; revisão diária mediana até dez minutos.
**Constraints**: nenhuma candidatura automática; aprovação específica de envios; nenhuma migração automática para consumo pago; isolamento de instruções externas; funcionamento dependente do computador ligado.
**Scale/Scope**: quatro propostas iniciais de bots; no máximo dez novas seleções por dia em America/Sao_Paulo; todas as seis jornadas na V1.

## Constitution Check

Pré-pesquisa: constituição é template sem princípios ratificados; não há regras constitucionais adicionais verificáveis. Não interpretar exemplos como exigências aprovadas.

Gate da especificação: bloqueado. PT-001–PT-003 exigem demonstração no ambiente alvo, ainda indisponível. A skill exige erro em clarificações não resolvidas e pesquisa concluída antes da fase 1. Não declarar aprovação pós-design: fase 1 não executada.

## Project Structure

Documentação atual:

```text
specs/001-assistente-busca-emprego/
  spec.md
  plan.md
  research.md
```

Não existe código de aplicação ou manifesto de dependências no workspace inspecionado. Estrutura de código, data-model.md, contracts/ e quickstart.md ficam pendentes da resolução do gate; não escolher transporte ou API fictícios para completar o template.

## Phase 0: Research

Ver [research.md](research.md). Próximo passo: com instalação OpenBot 0.70.0 identificada e uso de Codex/Claude confirmado, localizar interface suportada e executar uma prova mínima sem envio externo nem contratação. Registrar capacidades efetivamente verificadas e restrições.

## Phase 1: Design & Contracts

Não iniciada. Após resolver a pesquisa, definir entidades, invariantes de idempotência e aprovação, interfaces privadas e roteiro executável das jornadas. Reavaliar os gates antes de concluir.

## Complexity Tracking

Sem exceções constitucionais propostas. Nenhuma limitação central foi removida do escopo.

