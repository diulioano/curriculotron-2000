import type { ApplicationStatus } from '../api/panel';
export const APPLICATION_STATUSES: Record<ApplicationStatus, string> = {
  submitted_awaiting: 'Enviada · aguardando retorno',
  contact_received: 'Contato recebido',
  interview: 'Entrevista',
  offer: 'Proposta',
  closed_rejected: 'Encerrada · não selecionado',
  withdrawn: 'Desisti da candidatura',
};
export const PAUSE_REASONS: Record<string, string> = {
  provider_limit: 'Limite do provedor',
  needs_person: 'Precisa da sua atenção',
  source_unavailable: 'Fonte indisponível',
  gmail_disconnected: 'Gmail desconectado',
  machine_offline: 'Computador desligado',
};
