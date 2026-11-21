package dev.curriculotron.opportunities.adapter.out.persistence;

import dev.curriculotron.opportunities.domain.CorrectableOpportunityField;

/** Linha da projeção distinta de campos corrigidos por vaga. */
record CorrectedFieldRow(String opportunityId, CorrectableOpportunityField fieldName) {}
