---
name: busca-vagas
description: Buscar, deduplicar e priorizar vagas para o Diuliano e montar a seleção diária de no máximo dez vagas novas. Use quando pedirem busca de vagas, seleção do dia ou avaliação de uma vaga.
---

# Busca de vagas

Siga sempre a skill `regras-busca-emprego`.

## Fontes permitidas

1. **Gupy**: páginas públicas de carreira das empresas (`<empresa>.gupy.io`) e o portal público de vagas da Gupy, sem login.
2. **Sites de carreira** das empresas prioritárias (PicPay, BV, CWI, DB, CI&T, SAP — DB: db.gupy.io, db.tec.br; LinkedIn linkedin.com/company/somosdb só como referência) e de empresas sugeridas, incluindo boards públicos de ATS (Greenhouse, Lever, Workable, SuccessFactors).
3. **LinkedIn**: apenas links/textos que o usuário colar ou alertas que ele encaminhar. Não navegue logado nem raspe o LinkedIn.
4. Vagas que o usuário colar no chat.

Se uma fonte falhar, siga com as outras e informe qual não foi consultada. Nunca diga que a cobertura foi completa.

### Endpoints oficiais verificados

Use estes endpoints ao consultar as empresas prioritárias; não substitua por agregadores:

- BV: `jobs.lever.co/bv`.
- CI&T: `jobs.lever.co/ciandt`.
- PicPay: página de carreiras com board público Oracle Recruiting, site `CX_3001` (`epdm.fa.la1/CX_3001`).
- CWI: `cwi.com.br/talentos/oportunidades/`.
- DB: `db.gupy.io` (site institucional `db.tec.br`).
- SAP: acesso automatizado a `jobs.sap.com` está bloqueado; use somente entrada manual fornecida pelo usuário.

Indeed e outros agregadores não são fontes permitidas. Não os consulte nem use suas pistas como vagas verificadas. Se a página oficial não fornecer vagas verificáveis, registre seleção vazia para essa fonte.

## Fluxo

- **Período da busca manual**: padrão dos últimos 30 dias. Se o usuário informar datas inicial/final ou outro intervalo, aplique exatamente o período pedido.
- **Data de publicação**: extraia quando a fonte informar. Se não houver data, marque-a como desconhecida; não diga que a vaga está dentro do período escolhido.
- **Frequência**: rotina diária automática fica desligada até o usuário habilitá-la quando considerar que já há uma base suficiente. Não transforme toda busca manual em rotina.

Os passos numerados abaixo completam a coleta, deduplicação, filtragem e seleção.

1. Para cada vaga encontrada, extraia: título, empresa, local, modalidade, contrato, salário (texto original), requisitos (fatos), inferências (separadas), disponibilidade, link e data de consulta.
2. **Deduplicar**: mesma URL canônica (sem parâmetros de rastreamento) ou mesmo `fonte + id_externo` = mesma vaga. Se só parecer igual (mesmo título e empresa), não junte: registre em `pendencias` tipo `possivel_duplicata`.
3. **Excluir** de recomendações vagas com `decisao` = `descartada` ou `candidatada`, e vagas que já estiveram em qualquer `selecao_diaria` anterior.
4. **Filtrar** pelas regras de local e salário. Classifique `aderencia` forte/media/fraca com `motivo` objetivo.
5. **Selecionar** para hoje (data local America/Sao_Paulo): de 5 a 10 vagas **fortes**; se houver menos, aceite menos. **Nunca** ultrapasse 10 linhas em `selecao_diaria` para a data, somando buscas anteriores do mesmo dia. Excedentes ficam como lista secundária (vagas sem seleção).
6. Grave em `vagas` e `selecao_diaria`.

## Resposta ao usuário

Inclua a data de publicação quando a fonte a informar; quando desconhecida, diga isso separadamente da data de consulta.

Lista numerada da seleção, cada item com: título — empresa · local/modalidade · contrato · salário (ou "salário a confirmar") · por que foi recomendada · requisitos que ele não demonstra no currículo (como lacuna) · link · consultada em. Depois: quantas ficaram na lista secundária, fontes consultadas e fontes com falha. Seleção vazia é válida: diga isso e não fabrique vagas.

Quando o usuário disser "descarta a N" ou "me candidatei à N", atualize `decisao` (e para candidatura, avise que Mu de Áries (Acompanhamento) registra o histórico; você pode criar a linha em `candidaturas` com `status = enviada_aguardando` e `confirmada_em` = agora).
