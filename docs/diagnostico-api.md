# Diagnóstico da API Socorro

Data da verificação: 19/10/2026.

Endpoint fornecido: `https://ideaberta.com.br/socorro/`.

## Resultados observados

- POST HTTPS: falha de negociação TLS antes de receber uma confirmação, tanto no computador quanto no teste Android.
- GET no endereço HTTP alternativo do Swagger: resposta HTML intitulada Verificação de segurança, com formulário de CAPTCHA, em vez do painel Socorro.
- POST HTTP: HTTP 405 Not Allowed, retornado pelo servidor nginx. Não houve confirmação JSON de gravação.
- Os pedidos usados no diagnóstico eram registros normais (`tipo_registro: 1`) identificados como testes de integração, sem localização, SMS ou alerta de emergência.

Esses resultados descrevem a rede e o horário dos testes. Uma proteção por rede ou endereço IP pode alterar o comportamento em outras conexões.

## Verificação solicitada ao responsável pelo servidor

Confirmar o endereço vigente do POST e GET da API, corrigir a negociação HTTPS e verificar se a rota de integração está sendo tratada como uma página protegida por CAPTCHA. A versão 1.3.1 aceita `status: sucesso`, como a integração anterior. O ID é usado quando informado. Nome e tipo retornados servem para conferência e eventuais diferenças geram avisos, sem reenviar uma gravação já aceita.

## Comportamento do aplicativo

O nome está no campo `usuario`; o aplicativo inclui os dados disponíveis com os nomes e unidades do Swagger. A fila só é removida após uma confirmação válida. Falhas de HTTPS, HTTP e respostas HTML aparecem na tela e os dados permanecem disponíveis para reenvio. A fila e o nome foram testados com transporte simulado; isso não confirma recebimento real enquanto o endpoint estiver inacessível.

A observação de que o envio funcionava no aparelho antes das alterações foi considerada: o diagnóstico desta rede não prova a causa do problema no celular. Foram restaurados a regra de confirmação original e o momento de envio do monitoramento, preservando a interface e o envio manual.
